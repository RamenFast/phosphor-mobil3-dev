//! Parse the platform's init export data. This module never evaluates shell text.
pub const LIMIT: usize = 65_536;
const KEYS: [&str; 4] = [
    "BOOTCLASSPATH",
    "DEX2OATBOOTCLASSPATH",
    "SYSTEMSERVERCLASSPATH",
    "STANDALONE_SYSTEMSERVER_JARS",
];

pub fn trusted_jar(regular: bool, uid: u32, gid: u32, mode: u32) -> bool {
    regular && matches!(uid, 0 | 1000) && matches!(gid, 0 | 1000) && mode & 0o022 == 0
}

fn name(s: &str) -> bool {
    !s.is_empty()
        && s != "."
        && s != ".."
        && s.bytes()
            .all(|c| c.is_ascii_alphanumeric() || b"._-".contains(&c))
}

pub fn jar_path(path: &str) -> bool {
    if path.len() > 4096 || !path.ends_with(".jar") {
        return false;
    }
    let parts = path.split('/').collect::<Vec<_>>();
    match parts.as_slice() {
        ["", "system", "framework", jar] => name(jar),
        ["", "apex", module, "javalib", jar] => {
            let mut split = module.split('@');
            let module_ok = name(split.next().unwrap_or_default());
            let version_ok = split
                .next()
                .is_none_or(|v| !v.is_empty() && v.bytes().all(|c| c.is_ascii_digit()));
            module_ok && version_ok && split.next().is_none() && name(jar)
        }
        _ => false,
    }
}

pub fn parse(text: &str) -> Result<Vec<String>, &'static str> {
    if text.is_empty()
        || text.len() > LIMIT
        || text.bytes().any(|c| c != b'\n' && !(32..=126).contains(&c))
    {
        return Err("platform_classpath_size_or_control");
    }
    let mut values = [None; 4];
    for line in text.lines() {
        let line = line
            .strip_prefix("export ")
            .ok_or("platform_classpath_export_syntax")?;
        let (key, value) = line
            .split_once(' ')
            .ok_or("platform_classpath_export_syntax")?;
        let index = KEYS
            .iter()
            .position(|k| *k == key)
            .ok_or("platform_classpath_unknown_export")?;
        if values[index].is_some() {
            return Err("platform_classpath_duplicate_export");
        }
        if !value.is_empty() && (value.split(':').count() > 256 || !value.split(':').all(jar_path))
        {
            return Err("platform_classpath_jar_path");
        }
        values[index] = Some(value);
    }
    (0..2)
        .map(|index| {
            let value = values[index]
                .filter(|s| !s.is_empty())
                .ok_or("platform_classpath_required_export")?;
            Ok(format!("{}={value}", KEYS[index]))
        })
        .collect()
}

#[cfg(test)]
mod tests {
    use super::*;
    const BOOT: &str = "/apex/com.android.art/javalib/core-oj.jar:/system/framework/framework.jar";
    #[test]
    fn measured_platform_owners_without_app_or_writable_code() {
        assert!(trusted_jar(true, 0, 0, 0o100644));
        assert!(trusted_jar(true, 1000, 1000, 0o100644));
        for (regular, uid, gid, mode) in [
            (false, 0, 0, 0o100644),
            (true, 10401, 10401, 0o100400),
            (true, 2000, 2000, 0o100644),
            (true, 0, 10401, 0o100644),
            (true, 1000, 1000, 0o100664),
            (true, 0, 0, 0o100646),
        ] {
            assert!(!trusted_jar(regular, uid, gid, mode));
        }
    }
    fn valid() -> String {
        format!("export BOOTCLASSPATH {BOOT}\nexport DEX2OATBOOTCLASSPATH {BOOT}\nexport SYSTEMSERVERCLASSPATH /system/framework/services.jar\nexport STANDALONE_SYSTEMSERVER_JARS \n")
    }
    #[test]
    fn platform_order_and_only_two_exports() {
        assert_eq!(
            parse(&valid()).unwrap(),
            [
                format!("BOOTCLASSPATH={BOOT}"),
                format!("DEX2OATBOOTCLASSPATH={BOOT}")
            ]
        );
    }
    #[test]
    fn missing_empty_and_duplicate_exports() {
        for bad in [
            String::new(),
            format!("export BOOTCLASSPATH {BOOT}\n"),
            valid().replace(
                &format!("DEX2OATBOOTCLASSPATH {BOOT}"),
                "DEX2OATBOOTCLASSPATH ",
            ),
            format!("{}export BOOTCLASSPATH {BOOT}\n", valid()),
        ] {
            assert!(parse(&bad).is_err(), "{bad}");
        }
    }
    #[test]
    fn no_shell_or_unknown_environment() {
        for extra in [
            "export LD_PRELOAD /system/framework/a.jar\n",
            "export CLASSPATH /system/framework/a.jar\n",
            "touch /data/a\n",
            "\n",
        ] {
            assert!(parse(&(valid() + extra)).is_err());
        }
        for bad in [
            "$(id)",
            "`id`",
            "/data/a.jar",
            "/system/framework/a.jar;id",
            "'/system/framework/a.jar'",
        ] {
            assert!(parse(&valid().replace(BOOT, bad)).is_err());
        }
    }
    #[test]
    fn no_traversal_empty_relative_or_non_jar_paths() {
        for path in [
            "",
            "a.jar",
            "/system/framework/../a.jar",
            "/system/framework//a.jar",
            "/system/framework/sub/a.jar",
            "/system/framework/a.so",
            "/apex/../javalib/a.jar",
            "/apex/a@1@2/javalib/a.jar",
            "/apex/a@x/javalib/a.jar",
            "/vendor/framework/a.jar",
            "/apex/a/bin/a.jar",
        ] {
            assert!(!jar_path(path), "{path}");
        }
        for path in [
            "/system/framework/a.jar",
            "/apex/com.android.art/javalib/core-oj.jar",
            "/apex/com.android.art@123/javalib/core-oj.jar",
        ] {
            assert!(jar_path(path));
        }
        for value in [
            format!("{BOOT}:"),
            format!(":{BOOT}"),
            format!("{BOOT}::{BOOT}"),
        ] {
            assert!(parse(&valid().replace(BOOT, &value)).is_err());
        }
    }
    #[test]
    fn size_controls_and_entry_count_are_bounded() {
        for c in (0u8..32).chain([127, 255]) {
            if c != b'\n' {
                assert!(parse(&(valid() + &char::from(c).to_string())).is_err());
            }
        }
        assert!(parse(&"x".repeat(LIMIT + 1)).is_err());
        let excess = std::iter::repeat_n("/system/framework/a.jar", 257)
            .collect::<Vec<_>>()
            .join(":");
        assert!(parse(&valid().replace(BOOT, &excess)).is_err());
    }
}
