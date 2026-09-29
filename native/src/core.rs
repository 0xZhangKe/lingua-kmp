//! Platform-independent detector ownership and result conversion, reusable by a future C ABI.
use crate::language::{language_from_id, language_id};
use lingua::{LanguageDetector, LanguageDetectorBuilder};
use std::collections::HashMap;
use std::sync::{Arc, LazyLock, Mutex};

#[derive(Debug)]
pub enum BridgeError {
    Argument(String),
    State(String),
    Allocation(String),
}
pub type Result<T> = std::result::Result<T, BridgeError>;

#[derive(Default)]
struct Registry {
    next: i64,
    detectors: HashMap<i64, Arc<Detector>>,
}
static REGISTRY: LazyLock<Mutex<Registry>> = LazyLock::new(|| Mutex::new(Registry::default()));

pub struct Detector {
    inner: LanguageDetector,
}

pub fn create(ids: &[i32], distance: f64, preload: bool, low: bool) -> Result<i64> {
    if ids.is_empty() || !(0.0..=0.99).contains(&distance) {
        return Err(BridgeError::Argument(
            "Expected at least one language and distance in 0.0..0.99".into(),
        ));
    }
    let languages = ids
        .iter()
        .map(|id| language_from_id(*id))
        .collect::<Result<Vec<_>>>()?;
    let mut builder = LanguageDetectorBuilder::from_languages(&languages);
    builder.with_minimum_relative_distance(distance);
    if preload {
        builder.with_preloaded_language_models();
    }
    if low {
        builder.with_low_accuracy_mode();
    }
    let detector = Arc::new(Detector {
        inner: builder.build(),
    });
    let mut registry = REGISTRY
        .lock()
        .map_err(|_| BridgeError::State("Detector registry poisoned".into()))?;
    let id = registry
        .next
        .checked_add(1)
        .ok_or_else(|| BridgeError::State("Handle space exhausted".into()))?;
    registry
        .detectors
        .try_reserve(1)
        .map_err(|e| BridgeError::Allocation(e.to_string()))?;
    registry.detectors.insert(id, detector);
    registry.next = id;
    Ok(id)
}

pub fn get(id: i64) -> Result<Arc<Detector>> {
    REGISTRY
        .lock()
        .map_err(|_| BridgeError::State("Detector registry poisoned".into()))?
        .detectors
        .get(&id)
        .cloned()
        .ok_or_else(|| BridgeError::State("Invalid or closed detector handle".into()))
}

pub fn destroy(id: i64) -> Result<()> {
    let detector = REGISTRY
        .lock()
        .map_err(|_| BridgeError::State("Detector registry poisoned".into()))?
        .detectors
        .remove(&id)
        .ok_or_else(|| BridgeError::State("Invalid or closed detector handle".into()))?;
    drop(detector);
    Ok(())
}

fn confidence(values: Vec<(lingua::Language, f64)>) -> Vec<f64> {
    values
        .into_iter()
        .flat_map(|(language, value)| [language_id(language) as f64, value])
        .collect()
}

fn segments(text: &str, results: Vec<lingua::DetectionResult>) -> Result<Vec<i32>> {
    let mut packed = Vec::new();
    // Walk each prefix only once; offsets from lingua are UTF-8 byte boundaries.
    let mut byte_index = 0;
    let mut utf16_index = 0;
    for result in results {
        let mut indices = [0; 2];
        for (slot, index) in indices
            .iter_mut()
            .zip([result.start_index(), result.end_index()])
        {
            let section = text
                .get(byte_index..index)
                .ok_or_else(|| BridgeError::State("Invalid native text offset".into()))?;
            utf16_index += section.encode_utf16().count();
            byte_index = index;
            *slot = i32::try_from(utf16_index)
                .map_err(|_| BridgeError::State("Text offset overflow".into()))?;
        }
        let words = i32::try_from(result.word_count())
            .map_err(|_| BridgeError::State("Word count overflow".into()))?;
        packed.extend([
            indices[0],
            indices[1],
            words,
            language_id(result.language()),
        ]);
    }
    Ok(packed)
}

impl Detector {
    pub fn detect(&self, text: &str) -> i32 {
        self.inner
            .detect_language_of(text)
            .map(language_id)
            .unwrap_or(0)
    }
    pub fn detect_parallel(&self, texts: &[String]) -> Vec<i32> {
        self.inner
            .detect_languages_in_parallel_of(texts)
            .into_iter()
            .map(|v| v.map(language_id).unwrap_or(0))
            .collect()
    }
    pub fn confidence(&self, text: &str, id: i32) -> Result<f64> {
        Ok(self
            .inner
            .compute_language_confidence(text, language_from_id(id)?))
    }
    pub fn confidence_parallel(&self, texts: &[String], id: i32) -> Result<Vec<f64>> {
        Ok(self
            .inner
            .compute_language_confidence_in_parallel(texts, language_from_id(id)?))
    }
    pub fn values(&self, text: &str) -> Vec<f64> {
        confidence(self.inner.compute_language_confidence_values(text))
    }
    pub fn values_parallel(&self, texts: &[String]) -> Vec<Vec<f64>> {
        self.inner
            .compute_language_confidence_values_in_parallel(texts)
            .into_iter()
            .map(confidence)
            .collect()
    }
    pub fn multiple(&self, text: &str) -> Result<Vec<i32>> {
        segments(text, self.inner.detect_multiple_languages_of(text))
    }
    pub fn multiple_parallel(&self, texts: &[String]) -> Result<Vec<Vec<i32>>> {
        texts
            .iter()
            .zip(self.inner.detect_multiple_languages_in_parallel_of(texts))
            .map(|(text, results)| segments(text, results))
            .collect()
    }
    pub fn unload(&self) {
        self.inner.unload_language_models();
    }
}
