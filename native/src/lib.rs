use lingua::{
    Language,
    LanguageDetector,
    LanguageDetectorBuilder,
};

pub fn test() {
    let detector = LanguageDetectorBuilder::from_all_languages()
        .build();

    let language = detector.detect_language_of(
        "今日はいい天気ですね",
    );

    println!("{language:?}");
}
