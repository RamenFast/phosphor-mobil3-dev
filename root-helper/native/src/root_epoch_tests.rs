use crate::*;

fn request(sequence: u64, epoch: u64) -> Vec<u8> {
    [
        42u64.to_le_bytes(),
        sequence.to_le_bytes(),
        epoch.to_le_bytes(),
    ]
    .concat()
}
fn ack(id: &[u8], control: u64, epoch: u64, next: u64) -> Vec<u8> {
    [
        id.to_vec(),
        [
            control.to_le_bytes(),
            epoch.to_le_bytes(),
            next.to_le_bytes(),
        ]
        .concat(),
    ]
    .concat()
}
fn pcm(id: &[u8], seq: u64, control: u64, epoch: u64, count: u32) -> Vec<u8> {
    [
        id.to_vec(),
        seq.to_le_bytes().to_vec(),
        16000u32.to_le_bytes().to_vec(),
        1u32.to_le_bytes().to_vec(),
        2u32.to_le_bytes().to_vec(),
        count.to_le_bytes().to_vec(),
        control.to_le_bytes().to_vec(),
        epoch.to_le_bytes().to_vec(),
        vec![0; count as usize * 2],
    ]
    .concat()
}
fn ready(mode: u32) -> (Session, Vec<u8>) {
    let selection = Selection {
        generation: 42,
        mode,
    };
    let id = selection.tag(10401, &"a".repeat(64));
    let mut s = Session {
        selection,
        ..Session::default()
    };
    s.helper(
        11,
        &[id.clone(), br#"{"pcm_epoch_schema":1}"#.to_vec()].concat(),
        &id,
        0,
    )
    .unwrap();
    (s, id)
}
fn started() -> (Session, Vec<u8>) {
    let (mut s, id) = ready(2);
    s.app(4, &request(1, 0), 0).unwrap();
    s.helper(17, &ack(&id, 1, 0, 0), &id, 1).unwrap();
    s.app(2, &42u64.to_le_bytes(), 2).unwrap();
    (s, id)
}
#[test]
fn initial_ack_then_start_and_read_pair_transition() {
    let (mut s, id) = ready(2);
    assert!(s.app(2, &42u64.to_le_bytes(), 0).is_err());
    assert!(s.helper(17, &ack(&id, 1, 0, 0), &id, 0).is_err());
    s.app(4, &request(1, 0), 0).unwrap();
    assert!(s.helper(15, &pcm(&id, 0, 1, 0, 1), &id, 0).is_err());
    s.helper(17, &ack(&id, 1, 0, 0), &id, 1).unwrap();
    assert!(s.helper(15, &pcm(&id, 0, 1, 0, 1), &id, 1).is_err());
    s.app(2, &42u64.to_le_bytes(), 2).unwrap();
    s.app(4, &request(2, 1 << 63), 125).unwrap();
    assert!(s.helper(15, &pcm(&id, 0, 2, 1 << 63, 1), &id, 126).is_err());
    s.helper(15, &pcm(&id, 0, 1, 0, 1), &id, 126).unwrap();
    assert!(s.helper(17, &ack(&id, 2, 1 << 63, 0), &id, 126).is_err());
    s.helper(17, &ack(&id, 2, 1 << 63, 1), &id, 127).unwrap();
    assert!(s.helper(15, &pcm(&id, 1, 1, 0, 1), &id, 128).is_err());
    s.helper(15, &pcm(&id, 1, 2, 1 << 63, 160), &id, 128)
        .unwrap();
    assert_eq!(s.sequence, 2);
    assert_eq!(s.pcm_at, 128);
}
#[test]
fn pending_deadline_ignores_heartbeat_pcm_progress_and_ack_liveness() {
    let (mut s, id) = started();
    s.app(4, &request(2, 5), 125).unwrap();
    for (sequence, now) in [250u64, 500, 750, 1000].into_iter().enumerate() {
        s.app(1, &42u64.to_le_bytes(), now).unwrap();
        s.helper(15, &pcm(&id, sequence as u64, 1, 0, 1), &id, now)
            .unwrap();
        s.helper(
            16,
            &[id.clone(), (sequence as u64).to_le_bytes().to_vec()].concat(),
            &id,
            now,
        )
        .unwrap();
        s.tick(now);
        assert!(s.failure.is_none());
    }
    s.tick(1125);
    assert_eq!(s.failure, Some("epoch_ack_timeout"));
    assert!(s.protocol_clean);
    s.helper(17, &ack(&id, 2, 5, 4), &id, 1125).unwrap();
    assert_eq!(s.pcm_at, 1000);
    assert!(s.app(2, &42u64.to_le_bytes(), 1126).is_err());
    let result = [id.clone(), b"{}".to_vec()].concat();
    s.helper(12, &result, &id, 1126).unwrap();
    s.reaped(0);
    assert!(s.cleanup_confirmed());
    assert!(!s.success());
    let (mut late, tag) = started();
    late.app(4, &request(2, 1), 125).unwrap();
    late.helper(17, &ack(&tag, 2, 1, 0), &tag, 1125).unwrap();
    assert_eq!(late.failure, Some("epoch_ack_timeout"));
}
#[test]
fn stop_pending_terminal_drain_result_cancellation_and_clean_latch() {
    for acknowledged in [false, true] {
        let (mut s, id) = started();
        s.app(4, &request(2, 1), 125).unwrap();
        s.app(3, &42u64.to_le_bytes(), 126).unwrap();
        let mut wire = Vec::new();
        if acknowledged {
            wire.extend(frame(17, &ack(&id, 2, 1, 0)).unwrap());
        }
        wire.extend(frame(12, &[id.clone(), b"{}".to_vec()].concat()).unwrap());
        let mut decoder = Decoder::default();
        for byte in wire {
            decoder.push(&[byte]).unwrap();
            if let Some((kind, payload)) = decoder.next_frame().unwrap() {
                s.helper(kind, &payload, &id, 127).unwrap();
            }
        }
        decoder.eof().unwrap();
        assert!(s.epoch.pending.is_none());
        assert!(s.helper(17, &ack(&id, 2, 1, 0), &id, 128).is_err());
        assert!(s.helper(15, &pcm(&id, 0, 1, 0, 1), &id, 128).is_err());
        s.reaped(0);
        assert!(s.cleanup_confirmed());
        let error = s.helper(17, &ack(&id, 2, 1, 0), &id, 129).unwrap_err();
        s.reject(129, error);
        assert!(!s.cleanup_confirmed());
    }
    let platform = include_str!("platform.rs");
    assert_eq!(
        platform.matches("match s.helper(k, &p, &id, now)").count(),
        2
    );
    assert!(platform.contains("for _ in 0..4"));
}
#[test]
fn malformed_epoch_metadata_old_shapes_exhaustion_and_other_modes() {
    for at in [0, 68, 76, 80, 88, 96] {
        let (mut s, id) = ready(2);
        s.app(4, &request(1, 0), 0).unwrap();
        let mut bad = ack(&id, 1, 0, 0);
        bad[at] ^= 1;
        assert!(s.helper(17, &bad, &id, 1).is_err(), "offset {at}");
    }
    for mode in [0, 1, 4, 5] {
        let (mut s, id) = ready(mode);
        assert!(s.app(4, &request(1, 0), 0).is_err());
        assert!(s.helper(17, &ack(&id, 1, 0, 0), &id, 1).is_err());
        s.app(2, &42u64.to_le_bytes(), 1).unwrap();
        assert!(s.helper(15, &pcm(&id, 0, 1, 0, 1), &id, 2).is_err());
    }
    let (mut s, id) = started();
    assert!(s.helper(15, &pcm(&id, 0, 1, 0, 1)[..106], &id, 3).is_err());
    for at in [104, 112] {
        let mut bad = pcm(&id, 0, 1, 0, 1);
        bad[at] ^= 1;
        assert!(s.helper(15, &bad, &id, 3).is_err());
    }
    s.sequence = i64::MAX as u64;
    assert!(s
        .helper(15, &pcm(&id, s.sequence, 1, 0, 1), &id, 3)
        .is_err());
    s.progress_sequence = i64::MAX as u64;
    assert!(s
        .helper(
            16,
            &[id.clone(), s.progress_sequence.to_le_bytes().to_vec()].concat(),
            &id,
            3
        )
        .is_err());
}
#[test]
fn java_wire_vectors_through_actual_decoder_at_every_split() {
    let Ok(path) = std::env::var("ROOT_EPOCH_VECTORS") else {
        return;
    };
    let wire: Vec<u8> = [
        "initial-ack.bin",
        "old-pcm.bin",
        "new-ack.bin",
        "new-pcm.bin",
    ]
    .into_iter()
    .flat_map(|name| std::fs::read(std::path::Path::new(&path).join(name)).unwrap())
    .collect();
    for split in 0..=wire.len() {
        let (mut s, id) = ready(2);
        s.app(4, &request(1, 0), 0).unwrap();
        let mut decoder = Decoder::default();
        let mut frames = 0;
        for part in [&wire[..split], &wire[split..]] {
            for byte in part {
                decoder.push(&[*byte]).unwrap();
                if let Some((kind, payload)) = decoder.next_frame().unwrap() {
                    s.helper(kind, &payload, &id, 130).unwrap();
                    if frames == 0 {
                        s.app(2, &42u64.to_le_bytes(), 130).unwrap();
                        s.app(4, &request(2, 1 << 63), 130).unwrap();
                    }
                    frames += 1;
                }
            }
        }
        decoder.eof().unwrap();
        assert_eq!(frames, 4);
        assert_eq!(s.sequence, 2);
    }
}
