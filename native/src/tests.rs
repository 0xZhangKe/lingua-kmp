use crate::{core::*, language::*};
use lingua::{Language, LanguageDetector};

#[test]
fn mapping_covers_upstream_and_kotlin() {
    let kotlin =
        include_str!("../../lingua-core/src/commonMain/kotlin/com/zhangke/lingua/Language.kt");
    assert_eq!(Language::all().len(), 75);
    for language in Language::all() {
        let id = language_id(language);
        assert_eq!(language_from_id(id).unwrap(), language);
        let iso = language.iso_code_639_1().to_string().to_lowercase();
        assert_eq!(
            id,
            ((iso.as_bytes()[0] as i32) << 8) | iso.as_bytes()[1] as i32
        );
        assert!(kotlin.contains(&format!(
            "{}(IsoCode6391.{}, IsoCode6393.{})",
            language.to_string().to_uppercase(),
            iso.to_uppercase(),
            language.iso_code_639_3().to_string().to_uppercase()
        )));
    }
    for (name, languages) in [
        ("allWithArabicScript", Language::all_with_arabic_script()),
        (
            "allWithCyrillicScript",
            Language::all_with_cyrillic_script(),
        ),
        (
            "allWithDevanagariScript",
            Language::all_with_devanagari_script(),
        ),
        ("allWithLatinScript", Language::all_with_latin_script()),
        (
            "allWithSingleUniqueScript",
            Language::all_with_single_unique_script(),
        ),
    ] {
        let line = kotlin
            .lines()
            .find(|line| line.contains(&format!("fun {name}(")))
            .unwrap();
        let names = line
            .split("setOf(")
            .nth(1)
            .unwrap()
            .trim_end_matches(')')
            .split(", ")
            .collect::<std::collections::HashSet<_>>();
        let expected = languages
            .into_iter()
            .map(|v| v.to_string().to_uppercase())
            .collect::<std::collections::HashSet<_>>();
        assert_eq!(names, expected.iter().map(String::as_str).collect());
    }
    assert!(language_from_id(0).is_err());
    fn assert_send_sync<T: Send + Sync>() {}
    assert_send_sync::<LanguageDetector>();
}

#[test]
fn lifecycle_arguments_and_inflight_ownership() {
    assert!(create(&[], 0.0, false, false).is_err());
    assert!(create(&[0], 0.0, false, false).is_err());
    for distance in [f64::NAN, f64::INFINITY, -0.1, 1.0] {
        assert!(create(&[language_id(Language::English)], distance, false, false).is_err());
    }
    assert!(get(0).is_err());
    assert!(destroy(-1).is_err());
    let id = create(
        &[
            language_id(Language::Japanese),
            language_id(Language::Chinese),
        ],
        0.0,
        false,
        false,
    )
    .unwrap();
    let detector = get(id).unwrap();
    destroy(id).unwrap();
    assert!(get(id).is_err());
    assert!(destroy(id).is_err());
    // An acquired Arc outlives registry removal, including racing destroy.
    assert_eq!(
        detector.detect("今日はいい天気ですね"),
        language_id(Language::Japanese)
    );
}

#[test]
fn detection_confidence_parallel_and_unicode() {
    let ids = [
        Language::English,
        Language::French,
        Language::Japanese,
        Language::Chinese,
    ]
    .map(language_id);
    let id = create(&ids, 0.0, false, false).unwrap();
    let detector = get(id).unwrap();
    let texts: Vec<String> = [
        "Hello, how are you today?",
        "Bonjour, comment allez-vous aujourd’hui ?",
        "今日はいい天気ですね",
        "这是一个用于测试语言识别的中文句子。",
        "",
    ]
    .map(String::from)
    .to_vec();
    assert_eq!(
        detector.detect_parallel(&texts),
        vec![ids[0], ids[1], ids[2], ids[3], 0]
    );
    for (text, actual) in texts.iter().zip(detector.values_parallel(&texts)) {
        let expected = detector.values(text);
        for pair in actual.as_chunks::<2>().0 {
            let other = expected
                .as_chunks::<2>()
                .0
                .iter()
                .find(|other| other[0] == pair[0])
                .unwrap();
            assert!((other[1] - pair[1]).abs() < 1e-12);
        }
    }
    for (text, actual) in texts
        .iter()
        .zip(detector.confidence_parallel(&texts, ids[0]).unwrap())
    {
        assert!((actual - detector.confidence(text, ids[0]).unwrap()).abs() < 1e-12);
    }
    assert_eq!(
        detector.multiple_parallel(&texts).unwrap(),
        texts
            .iter()
            .map(|s| detector.multiple(s).unwrap())
            .collect::<Vec<_>>()
    );
    assert_eq!(
        detector
            .confidence(&texts[0], language_id(Language::German))
            .unwrap(),
        0.0
    );
    assert!(detector.confidence(&texts[0], -1).is_err());
    let unicode = "😀 上海大学是一个好大学. It is such a great university.";
    let sections = detector.multiple(unicode).unwrap();
    assert_eq!(sections.len(), 8);
    assert_eq!(sections[3], language_id(Language::Chinese));
    assert_eq!(sections[7], language_id(Language::English));
    assert_eq!(
        sections[sections.len() - 3],
        unicode.encode_utf16().count() as i32
    );
    for chunk in sections.as_chunks::<4>().0 {
        assert!(chunk[0] < chunk[1]);
    }
    detector.unload();
    assert_eq!(detector.detect(&texts[0]), ids[0]);
    destroy(id).unwrap();
}

#[test]
fn low_accuracy_preload_and_relative_distance() {
    let ids = [Language::English, Language::French].map(language_id);
    let id = create(&ids, 0.99, true, true).unwrap();
    let detector = get(id).unwrap();
    assert_eq!(detector.detect("prologue"), 0);
    assert_eq!(detector.detect("This is a long English sentence about language detection. We are testing the ability of the detector to recognize English words reliably in low accuracy mode."), ids[0]);
    destroy(id).unwrap();
}

#[test]
fn file_writers_validate_upstream_nontermination_cases() {
    let directory = std::env::temp_dir().join(format!(
        "lingua-kmp-writers-{}-{}",
        std::process::id(),
        std::time::SystemTime::now()
            .duration_since(std::time::UNIX_EPOCH)
            .unwrap()
            .as_nanos()
    ));
    std::fs::create_dir(&directory).unwrap();
    let input = directory.join("input.txt");
    std::fs::write(
        &input,
        "Language detection works with interesting example sentences.\n",
    )
    .unwrap();
    let input = input.to_str().unwrap();
    let output = directory.to_str().unwrap();
    for count in [0, 2, u32::MAX as i64, -1, u32::MAX as i64 + 1] {
        assert!(matches!(
            crate::writers::test_data(input, output, r"\p{L}", count),
            Err(BridgeError::Argument(_))
        ));
    }
    assert!(!directory.join("sentences.txt").exists());
    crate::writers::test_data(input, output, r"\p{L}", 1).unwrap();
    assert!(directory.join("word-pairs.txt").metadata().unwrap().len() > 0);
    crate::writers::model(input, output, language_id(Language::English), r"\p{L}").unwrap();
    assert!(matches!(
        crate::writers::model(input, output, language_id(Language::English), "["),
        Err(BridgeError::Argument(_))
    ));
    std::fs::write(input, [0xff]).unwrap();
    assert!(matches!(
        crate::writers::test_data(input, output, r"\p{L}", 1),
        Err(BridgeError::Argument(_))
    ));
    std::fs::remove_dir_all(directory).unwrap();
}
