import 'dart:convert';
import 'dart:math';

import 'package:cryptography/cryptography.dart';

/// Matches Android [FocalSessionCrypto] / Python `derive_key`.
class FoclCrypto {
  static final _sha256 = Sha256();
  static final _aesGcm = AesGcm.with256bits();
  static const _tagLength = 16;
  static const _ivLength = 12;

  static Future<SecretKey> deriveKey(String pin) async {
    final digest = await _sha256.hash(utf8.encode('FOCL:$pin'));
    return SecretKey(digest.bytes);
  }

  static Future<List<int>> encrypt(SecretKey key, List<int> plaintext) async {
    final iv = List<int>.generate(_ivLength, (_) => Random.secure().nextInt(256));
    final secretBox = await _aesGcm.encrypt(
      plaintext,
      secretKey: key,
      nonce: iv,
    );
    return [...iv, ...secretBox.cipherText, ...secretBox.mac.bytes];
  }

  static Future<List<int>?> decrypt(SecretKey key, List<int> encrypted) async {
    if (encrypted.length <= _ivLength + _tagLength) return null;
    try {
      final iv = encrypted.sublist(0, _ivLength);
      final body = encrypted.sublist(_ivLength);
      final ciphertext = body.sublist(0, body.length - _tagLength);
      final tag = body.sublist(body.length - _tagLength);
      final secretBox = SecretBox(
        ciphertext,
        nonce: iv,
        mac: Mac(tag),
      );
      return await _aesGcm.decrypt(secretBox, secretKey: key);
    } catch (_) {
      return null;
    }
  }
}
