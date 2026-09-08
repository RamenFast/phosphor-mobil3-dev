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
}
