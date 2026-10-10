use jni::objects::{JObject, JString};
use jni::sys::{jint, jstring};
use jni::JNIEnv;
use moviebox_tui::providers::models::{PlaybackSource, ProviderKind, Release, ResolutionIntent};
use moviebox_tui::providers::ReleaseProvider;
use moviebox_tui::service::MovieBoxService;
use serde_json::{json, Value};
use std::panic::{catch_unwind, AssertUnwindSafe};

fn runtime() -> Result<tokio::runtime::Runtime, String> {
    tokio::runtime::Builder::new_multi_thread()
        .worker_threads(2)
        .enable_all()
        .build()
        .map_err(|e| format!("Could not start provider runtime: {e}"))
}

fn jstring_to_string(env: &mut JNIEnv<'_>, value: &JString<'_>) -> Result<String, String> {
    env.get_string(value)
        .map(|s| s.into())
        .map_err(|e| format!("Invalid input string: {e}"))
}

fn catalog_json(item: &moviebox_tui::providers::models::CatalogItem) -> Value {
    json!({
        "id": item.id.value.clone(),
        "provider": item.id.provider.cache_key(),
        "title": item.title.clone(),
        "mediaType": if item.media_type == moviebox_tui::providers::models::MediaType::Series { "tv" } else { "movie" },
        "year": item.year.clone().unwrap_or_default(),
        "posterUrl": item.poster_url.clone(),
        "sourceName": item.id.provider.label(),
    })
}

fn source_json(source: &PlaybackSource, label: &str) -> Value {
    let height = source.max_height.map(|v| v as i64).unwrap_or(0);
    let headers = source.headers.iter().map(|(k, v)| (k.clone(), v.clone())).collect::<std::collections::HashMap<_, _>>();
    json!({
        "url": source.url.clone(),
        "label": if label.is_empty() { source.source_label.clone() } else { label.to_string() },
        "height": height,
        "headers": headers,
    })
}

fn release_to_source(release: &Release) -> Option<PlaybackSource> {
    let mirror = release.mirrors.first()?;
    Some(PlaybackSource {
        provider: release.provider,
        url: mirror.resolver_url.clone(),
        headers: mirror.headers.clone(),
        subtitle: None,
        source_label: release.quality.clone().unwrap_or_else(|| mirror.label.clone()),
        max_height: Some(release.resolution_u64()),
    })
}

async fn search_provider(query: &str, provider: &str, page: usize) -> Result<Value, String> {
    let kind = ProviderKind::parse(provider).ok_or_else(|| format!("Unknown provider: {provider}"))?;
    if kind.is_bdix() {
        return Ok(json!([])); // BDIX mirrors are region-specific and disabled by default.
    }
    let service = MovieBoxService::new();
    let results = service.search_typed(kind, query, page).await.map_err(|e| e.to_string())?;
    Ok(Value::Array(results.iter().map(catalog_json).collect()))
}

async fn streams_for(provider: &str, id: &str, season: usize, episode: usize) -> Result<Value, String> {
    let kind = ProviderKind::parse(provider).ok_or_else(|| format!("Unknown provider: {provider}"))?;
    let service = MovieBoxService::new();
    let mut sources: Vec<PlaybackSource> = Vec::new();
    match kind {
        ProviderKind::MovieBox => {
            let releases = service.client.episode_streams(id, season, episode).await.map_err(|e| e.to_string())?;
            for release in &releases {
                if let Some(source) = release_to_source(release) { sources.push(source); }
            }
        }
        ProviderKind::FourKHdHub => {
            let client = service.fourk_client.as_ref().ok_or_else(|| "4KHDHub is unavailable".to_string())?;
            let releases = client.releases(id, season, episode).await.map_err(|e| e.to_string())?;
            for release in releases.iter().take(8) {
                if let Ok(source) = client.resolve_release(release, ResolutionIntent::Playback).await {
                    sources.push(source);
                }
            }
        }
        ProviderKind::Dramachi => {
            let releases = service.dramachi_client.episode_streams(id, season, episode).await.map_err(|e| e.to_string())?;
            for release in &releases {
                if let Some(source) = release_to_source(release) { sources.push(source); }
            }
        }
        ProviderKind::BdixCircleFtp | ProviderKind::BdixDhakaFlix => {
            return Err("BDIX sources require a supported Bangladeshi ISP and are disabled in Decan Movie by default.".into());
        }
        ProviderKind::Addons => return Err("Use the add-on provider configuration for community add-ons.".into()),
    }
    let mut seen = std::collections::HashSet::new();
    sources.retain(|s| s.url.starts_with("https://") && seen.insert(s.url.clone()));
    if sources.is_empty() { return Err("The provider returned no playable streams for this title.".into()); }
    sources.sort_by_key(|s| std::cmp::Reverse(s.max_height.unwrap_or(0)));
    Ok(Value::Array(sources.iter().map(|s| source_json(s, &s.source_label)).collect()))
}

fn return_json(env: &mut JNIEnv<'_>, value: Value) -> jstring {
    let text = value.to_string();
    match env.new_string(text) {
        Ok(s) => s.into_raw(),
        Err(_) => std::ptr::null_mut(),
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_decanmoviebox_NativeMovieBox_searchNative(
    mut env: JNIEnv<'_>, _this: JObject<'_>, query: JString<'_>, provider: JString<'_>, page: jint,
) -> jstring {
    let result = catch_unwind(AssertUnwindSafe(|| {
        let query = jstring_to_string(&mut env, &query)?;
        let provider = jstring_to_string(&mut env, &provider)?;
        let rt = runtime()?;
        rt.block_on(search_provider(&query, &provider, page.max(1) as usize))
    }));
    let value = match result {
        Ok(Ok(value)) => value,
        Ok(Err(error)) => json!({"error": error}),
        Err(_) => json!({"error": "Provider bridge failed unexpectedly."}),
    };
    return_json(&mut env, value)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_decanmoviebox_NativeMovieBox_streamsNative(
    mut env: JNIEnv<'_>, _this: JObject<'_>, provider: JString<'_>, id: JString<'_>, season: jint, episode: jint,
) -> jstring {
    let result = catch_unwind(AssertUnwindSafe(|| {
        let provider = jstring_to_string(&mut env, &provider)?;
        let id = jstring_to_string(&mut env, &id)?;
        let rt = runtime()?;
        rt.block_on(streams_for(&provider, &id, season.max(0) as usize, episode.max(0) as usize))
    }));
    let value = match result {
        Ok(Ok(value)) => value,
        Ok(Err(error)) => json!({"error": error}),
        Err(_) => json!({"error": "Stream resolver failed unexpectedly."}),
    };
    return_json(&mut env, value)
}
