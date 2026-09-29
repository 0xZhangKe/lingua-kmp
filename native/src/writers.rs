//! Shared wrappers for upstream's public file-generation tools.
use crate::{
    core::{BridgeError, Result},
    language::language_from_id,
};
use std::{collections::HashSet, path::Path};

fn output_path(output: &str) -> Result<&Path> {
    let path = Path::new(output);
    if !path.is_absolute() || !path.is_dir() {
        return Err(BridgeError::Argument(
            "Output must be an existing absolute directory".into(),
        ));
    }
    Ok(path)
}

fn input_path(input: &str) -> Result<&Path> {
    let path = Path::new(input);
    if !path.is_absolute() || !path.is_file() {
        return Err(BridgeError::Argument(
            "Input must be an existing absolute file".into(),
        ));
    }
    Ok(path)
}

fn io_result(result: std::io::Result<()>) -> Result<()> {
    result.map_err(|error| BridgeError::State(format!("File generation failed: {error}")))
}

fn validate_text(input: &Path, pattern: &str) -> Result<usize> {
    // Validate the same expression upstream compiles, before any output file is overwritten.
    regex::Regex::new(pattern).map_err(|error| BridgeError::Argument(error.to_string()))?;
    use std::io::BufRead;
    let file = std::fs::File::open(input).map_err(|error| BridgeError::State(error.to_string()))?;
    let mut line_count = 0;
    for line in std::io::BufReader::new(file).lines() {
        line_count += 1;
        line.map_err(|error| {
            if error.kind() == std::io::ErrorKind::InvalidData {
                BridgeError::Argument("Input text must be valid UTF-8".into())
            } else {
                BridgeError::State(error.to_string())
            }
        })?;
    }
    Ok(line_count)
}

pub fn model(input: &str, output: &str, language: i32, char_class: &str) -> Result<()> {
    let input = input_path(input)?;
    let output = output_path(output)?;
    let language = language_from_id(language)?;
    validate_text(input, &format!("^[{char_class}]+$"))?;
    io_result(
        lingua::LanguageModelFilesWriter::create_and_write_language_model_files(
            input, output, language, char_class,
        ),
    )
}

pub fn test_data(input: &str, output: &str, char_class: &str, maximum_lines: i64) -> Result<()> {
    let input = input_path(input)?;
    let output = output_path(output)?;
    let maximum_lines = u32::try_from(maximum_lines)
        .map_err(|_| BridgeError::Argument("maximumLines must fit UInt".into()))?;
    let line_count = validate_text(input, &format!("[{char_class}]{{5,}}"))?;
    // lingua 1.8.0 samples distinct line numbers until maximum_lines is reached.
    // Zero or a count above the input length never terminates; reject before writing.
    if maximum_lines == 0 || maximum_lines as usize > line_count {
        return Err(BridgeError::Argument(
            "maximumLines must be in 1..input line count (upstream 1.8.0 limitation)".into(),
        ));
    }
    io_result(
        lingua::TestDataFilesWriter::create_and_write_test_data_files(
            input,
            output,
            char_class,
            maximum_lines,
        ),
    )
}

pub fn unique(output: &str) -> Result<()> {
    io_result(lingua::UniqueNgramsWriter::create_and_write_unique_ngram_files(output_path(output)?))
}

pub fn most_common(output: &str, ids: &[i32], count: i64) -> Result<()> {
    let output = output_path(output)?;
    let languages = ids
        .iter()
        .map(|id| language_from_id(*id))
        .collect::<Result<HashSet<_>>>()?;
    let count = u32::try_from(count)
        .map_err(|_| BridgeError::Argument("mostCommon must fit UInt".into()))?;
    if languages.is_empty() || count == 0 {
        return Err(BridgeError::Argument(
            "Expected languages and positive mostCommon".into(),
        ));
    }
    io_result(
        lingua::MostCommonNgramsWriter::create_and_write_most_common_ngram_files(
            output, &languages, count,
        ),
    )
}
