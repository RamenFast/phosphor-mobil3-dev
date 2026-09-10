//! Surface alpha selection is shared by host fixtures and Android presentation.
pub fn alpha_mode(
    transparent: bool,
    supported: &[wgpu::CompositeAlphaMode],
) -> Option<wgpu::CompositeAlphaMode> {
    use wgpu::CompositeAlphaMode::{Opaque, PreMultiplied};
    if transparent && supported.contains(&PreMultiplied) {
        Some(PreMultiplied)
    } else if supported.contains(&Opaque) {
        Some(Opaque)
    } else {
        // Alpha-one output remains solid with any explicit compositor mode.
        supported
            .iter()
            .copied()
            .find(|mode| *mode != wgpu::CompositeAlphaMode::Auto)
    }
}

pub fn scope_alpha(requested_transparent: bool, active: wgpu::CompositeAlphaMode) -> f32 {
    if requested_transparent && active == wgpu::CompositeAlphaMode::PreMultiplied {
        0.0
    } else {
        1.0
    }
}


#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Transfer {
    ManualSdr,
    HardwareSrgb,
    LinearScRgb,
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub struct OutputChoice {
    pub transfer: Transfer,
    pub use_fp16: bool,
    pub reason: &'static str,
}

/// Requested HDR may attempt linear FP16 only on Vulkan with an actual pair and API 34+.
/// Otherwise keep the request and use explicit SDR. Format lists are the surface capabilities.
pub fn choose_output(requested_hdr: bool, vulkan: bool, api: i32, has_rgba16float: bool) -> OutputChoice {
    if !requested_hdr {
        return OutputChoice { transfer: Transfer::HardwareSrgb, use_fp16: false, reason: "hdr_off" };
    }
    if !vulkan {
        return OutputChoice { transfer: Transfer::HardwareSrgb, use_fp16: false, reason: "non_vulkan" };
    }
    if !has_rgba16float {
        return OutputChoice { transfer: Transfer::HardwareSrgb, use_fp16: false, reason: "no_fp16_pair" };
    }
    if api < 34 {
        return OutputChoice { transfer: Transfer::HardwareSrgb, use_fp16: false, reason: "metadata_api" };
    }
    OutputChoice { transfer: Transfer::LinearScRgb, use_fp16: true, reason: "attempt_linear" }
}

#[cfg(test)]
mod tests {
    use super::*;
    use wgpu::CompositeAlphaMode::*;
    #[test]
    fn only_confirmed_transparency_clears_the_scope_backplate() {
        for mode in [Auto, Opaque, PreMultiplied, PostMultiplied, Inherit] {
            assert_eq!(scope_alpha(false, mode), 1.0);
            assert_eq!(
                scope_alpha(true, mode),
                if mode == PreMultiplied { 0.0 } else { 1.0 }
            );
        }
    }
    #[test]
    fn transparent_requires_premultiplied_not_first_capability() {
        assert_eq!(
            alpha_mode(true, &[Opaque, PostMultiplied, PreMultiplied]),
            Some(PreMultiplied)
        );
        assert_eq!(alpha_mode(true, &[PostMultiplied, Opaque]), Some(Opaque));
        assert_eq!(alpha_mode(false, &[PreMultiplied, Opaque]), Some(Opaque));
        assert_eq!(alpha_mode(false, &[PreMultiplied]), Some(PreMultiplied));
        assert_eq!(alpha_mode(true, &[]), None);
        assert_eq!(alpha_mode(true, &[Auto]), None);
    }
    #[test]
    fn hdr_attempt_requires_vulkan_fp16_and_api34() {
        assert_eq!(choose_output(false, true, 34, true).reason, "hdr_off");
        assert!(!choose_output(false, true, 34, true).use_fp16);
        assert_eq!(choose_output(true, false, 34, true).reason, "non_vulkan");
        assert_eq!(choose_output(true, true, 34, false).reason, "no_fp16_pair");
        assert_eq!(choose_output(true, true, 33, true).reason, "metadata_api");
        assert!(!choose_output(true, true, 33, true).use_fp16);
        let yes = choose_output(true, true, 34, true);
        assert!(yes.use_fp16);
        assert_eq!(yes.transfer, Transfer::LinearScRgb);
        assert_eq!(yes.reason, "attempt_linear");
    }
}
