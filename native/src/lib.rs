mod c_bridge;
mod core;
#[cfg(any(
    target_os = "android",
    target_os = "macos",
    target_os = "linux",
    target_os = "windows"
))]
mod jni_bridge;
mod language;
mod writers;

#[cfg(test)]
mod tests;
