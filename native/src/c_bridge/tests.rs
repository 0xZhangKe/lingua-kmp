use super::*;
use std::ptr;

struct Owned(*mut LinguaResult);
impl Owned {
    fn success(raw: *mut LinguaResult) -> Self {
        let value = Self(raw);
        assert_eq!(
            unsafe { lingua_result_status(raw) },
            0,
            "{}",
            unsafe { &*raw }.message.to_string_lossy()
        );
        value
    }
    fn handle(&self) -> i64 {
        unsafe { lingua_result_handle(self.0) }
    }
    fn rows(&self) -> Vec<Vec<f64>> {
        unsafe { &*self.0 }.payload.rows.clone()
    }
}
impl Drop for Owned {
    fn drop(&mut self) {
        unsafe { lingua_result_free(self.0) }
    }
}
fn text(units: &[u16]) -> LinguaText {
    LinguaText {
        data: units.as_ptr(),
        len: units.len() as u64,
    }
}
fn utf16(value: &str) -> Vec<u16> {
    value.encode_utf16().collect()
}

#[test]
fn c_abi_all_detector_operations_and_owned_results() {
    unsafe {
        let ids = [25966, 26226, 27233, 31336];
        let created = Owned::success(lingua_create(ids.as_ptr(), ids.len() as u64, 0.0, 0, 0));
        let handle = created.handle();
        let inputs = [
            utf16("Hello, how are you today?"),
            utf16("Bonjour, comment allez-vous aujourd’hui ?"),
            utf16("今日はいい天気ですね"),
            utf16("这是一个用于测试语言识别的中文句子。"),
            vec![],
        ];
        let texts: Vec<_> = inputs.iter().map(|s| text(s)).collect();
        assert_eq!(
            Owned::success(lingua_detect_parallel(
                handle,
                texts.as_ptr(),
                texts.len() as u64
            ))
            .rows(),
            vec![vec![25966.0, 26226.0, 27233.0, 31336.0, 0.0]]
        );
        let parallel_confidence = Owned::success(lingua_confidence_parallel(
            handle,
            texts.as_ptr(),
            texts.len() as u64,
            25966,
        ))
        .rows();
        let parallel_values = Owned::success(lingua_values_parallel(
            handle,
            texts.as_ptr(),
            texts.len() as u64,
        ))
        .rows();
        let parallel_segments = Owned::success(lingua_multiple_parallel(
            handle,
            texts.as_ptr(),
            texts.len() as u64,
        ))
        .rows();
        for (index, input) in texts.iter().enumerate() {
            assert_eq!(
                Owned::success(lingua_detect(handle, *input)).rows()[0][0],
                [25966.0, 26226.0, 27233.0, 31336.0, 0.0][index]
            );
            assert!(
                (Owned::success(lingua_confidence(handle, *input, 25966)).rows()[0][0]
                    - parallel_confidence[0][index])
                    .abs()
                    < 1e-12
            );
            let single_values = Owned::success(lingua_values(handle, *input)).rows();
            for pair in single_values[0].as_chunks::<2>().0 {
                let parallel = parallel_values[index]
                    .as_chunks::<2>()
                    .0
                    .iter()
                    .find(|p| p[0] == pair[0])
                    .unwrap();
                assert!((pair[1] - parallel[1]).abs() < 1e-12);
            }
            assert_eq!(
                Owned::success(lingua_multiple(handle, *input)).rows()[0],
                parallel_segments[index]
            );
        }
        for operation in [
            lingua_detect_parallel,
            lingua_values_parallel,
            lingua_multiple_parallel,
        ] {
            assert!(
                Owned::success(operation(handle, ptr::null(), 0))
                    .rows()
                    .iter()
                    .all(Vec::is_empty)
            );
        }
        assert_eq!(
            Owned::success(lingua_confidence_parallel(handle, ptr::null(), 0, 25966)).rows(),
            vec![vec![]]
        );
        Owned::success(lingua_unload(handle));
        assert_eq!(
            Owned::success(lingua_detect(handle, texts[0])).rows(),
            vec![vec![25966.0]]
        );
        // A returned allocation remains valid after the detector is destroyed.
        let result = Owned::success(lingua_values(handle, texts[0]));
        Owned::success(lingua_destroy(handle));
        assert_eq!(lingua_result_rows(result.0), 1);
        assert_eq!(lingua_result_row_len(result.0, 0), 8);
        assert_eq!(*lingua_result_row_data(result.0, 0), 25966.0);
        assert!(lingua_result_row_data(result.0, u64::MAX).is_null());
        let calls = [
            lingua_detect(handle, texts[0]),
            lingua_detect_parallel(handle, ptr::null(), 0),
            lingua_confidence(handle, texts[0], 25966),
            lingua_confidence_parallel(handle, ptr::null(), 0, 25966),
            lingua_values(handle, texts[0]),
            lingua_values_parallel(handle, ptr::null(), 0),
            lingua_multiple(handle, texts[0]),
            lingua_multiple_parallel(handle, ptr::null(), 0),
            lingua_unload(handle),
            lingua_destroy(handle),
        ];
        for raw in calls {
            let result = Owned(raw);
            assert_eq!(lingua_result_status(result.0), 2);
        }
    }
}

#[test]
fn c_abi_utf16_offsets_nul_and_invalid_arguments() {
    unsafe {
        let ids = [25966, 31336];
        let created = Owned::success(lingua_create(ids.as_ptr(), 2, 0.0, 0, 0));
        let handle = created.handle();
        let mixed = utf16("😀 上海大学是一个好大学. It is such a great university.");
        let rows = Owned::success(lingua_multiple(handle, text(&mixed))).rows();
        let segments: Vec<_> = rows[0].as_chunks::<4>().0.iter().collect();
        assert!(segments.len() >= 2);
        assert_eq!(segments[0][0], 0.0);
        assert_eq!(segments.last().unwrap()[1], mixed.len() as f64);
        assert!(segments.windows(2).all(|s| s[0][1] == s[1][0]));
        Owned::success(lingua_detect(handle, text(&utf16("Hello\0 world 😀"))));
        let bad = [0xD800];
        let bad_texts = [text(&mixed), text(&bad)];
        let errors = [
            lingua_create(ptr::null(), 1, 0.0, 0, 0),
            lingua_create(ids.as_ptr(), 2, f64::NAN, 0, 0),
            lingua_detect(handle, text(&bad)),
            lingua_detect(
                handle,
                LinguaText {
                    data: ptr::null(),
                    len: 1,
                },
            ),
            lingua_detect(
                handle,
                LinguaText {
                    data: mixed.as_ptr(),
                    len: u64::MAX,
                },
            ),
            lingua_detect_parallel(handle, bad_texts.as_ptr(), 2),
            lingua_confidence(handle, text(&mixed), 0),
        ];
        for raw in errors {
            let result = Owned(raw);
            assert_eq!(lingua_result_status(result.0), 1);
            assert!(
                !std::ffi::CStr::from_ptr(lingua_result_message(result.0))
                    .to_bytes()
                    .is_empty()
            );
        }
        Owned::success(lingua_destroy(handle));
        lingua_result_free(ptr::null_mut());
        let panic = Owned(boundary(|| panic!("test boundary")));
        assert_eq!(lingua_result_status(panic.0), 2);
    }
}

#[test]
fn c_abi_file_writers_validate_and_generate_files() {
    unsafe {
        let dir = std::env::temp_dir().join(format!("lingua-c-{}", std::process::id()));
        std::fs::create_dir_all(&dir).unwrap();
        let input_path = dir.join("input.txt");
        std::fs::write(&input_path, "Language detection works with interesting example sentences and several different words.\n").unwrap();
        let input = utf16(input_path.to_str().unwrap());
        let output = utf16(dir.to_str().unwrap());
        let pattern = utf16("\\p{L}");
        Owned::success(lingua_write_test_data(
            text(&input),
            text(&output),
            text(&pattern),
            1,
        ));
        assert!(dir.join("sentences.txt").is_file());
        Owned::success(lingua_write_model(
            text(&input),
            text(&output),
            25966,
            text(&pattern),
        ));
        let languages = [25966];
        Owned::success(lingua_write_most_common(
            text(&output),
            languages.as_ptr(),
            1,
            10,
        ));
        let result = Owned(lingua_write_unique(text(&utf16("relative-path"))));
        assert_eq!(lingua_result_status(result.0), 1);
        let result = Owned(lingua_write_test_data(
            text(&input),
            text(&output),
            text(&pattern),
            0,
        ));
        assert_eq!(lingua_result_status(result.0), 1);
        std::fs::write(&input_path, "tiny\n").unwrap();
        let result = Owned(lingua_write_test_data(
            text(&input),
            text(&output),
            text(&pattern),
            1,
        ));
        assert_eq!(lingua_result_status(result.0), 2); // upstream panic stays inside the ABI
        std::fs::remove_dir_all(dir).unwrap();
    }
}
