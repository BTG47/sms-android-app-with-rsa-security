# RSA and ECC Implementation, Message Format and Performance Evaluation

*Section author: José (Persona 2). Draft for the consolidated report. Figures are in `results/report/`.
Android measurements will be added when Bruno's phone run is available; every number below was measured on a PC JVM.*

## 1. Methodology

### 1.1 Cryptographic design

The cryptographic layer is a standalone Kotlin/JVM library (`:crypto`) that depends only on the standard Java
Cryptography Architecture, which Android also provides, so the same code is unit-tested on a PC and runs inside
the Android application without changes. It offers four configurations (profiles):

| Profile | Key protection | Content protection | Approx. security strength [5] |
|---|---|---|---:|
| RSA-2048 | RSA-OAEP (SHA-256 digest, MGF1-SHA-1) encrypts a random 256-bit seed; HKDF-SHA-256 derives the key | AES-256-GCM | 112 bits |
| RSA-3072 | same as above | AES-256-GCM | 128 bits |
| ECC P-256 | Ephemeral-static ECDH on P-256; HKDF-SHA-256 (ECIES-style) | AES-256-GCM | 128 bits |
| ECC P-384 | Ephemeral-static ECDH on P-384; HKDF-SHA-384 (ECIES-style) | AES-256-GCM | 192 bits |

Both families follow the same hybrid KEM/DEM pattern. The asymmetric algorithm is used only to establish a fresh
per-message secret: RSA-OAEP encrypts a random seed, and in the ECC case the sender generates an ephemeral key
pair and performs ECDH with the recipient's static public key. HKDF [7] turns that secret into a 256-bit AES key and a
96-bit GCM nonce. AES-GCM [6] then encrypts the message and authenticates it together with all metadata. Because a
new AES key is derived for every message, deriving the nonce from the same HKDF output is safe and saves 12 bytes
per SMS. ECC does not provide a direct encryption primitive; the ECDH + KDF + authenticated cipher construction is
the standard way to encrypt with elliptic curves, as also noted in [3].

The OAEP parameters are set explicitly (SHA-256 digest, MGF1 with SHA-1, empty label) because Java providers and Android Keystore choose different defaults for "OAEP with SHA-256", and Android Keystore supports MGF1 only with SHA-1 before API 35. The Android side confirmed this configuration works on both test phones (Samsung and POCO). Using SHA-1 inside MGF1 does not weaken OAEP, because MGF1 needs only a pseudorandom mask generator, not collision resistance [8].

The roles of each operation are:
- **Key generation** creates the long-term recipient key pair (RSA modulus with e = 65537, or a NIST curve point).
- **Asymmetric operation**: RSA-OAEP encryption/decryption of the seed, or ephemeral key generation + ECDH
  (sender) and point decompression + ECDH (recipient).
- **Key derivation (HKDF)** binds the shared secret to the protocol label, the message header, and both public keys.
- **AES-256-GCM** provides confidentiality and integrity of the text and of the header.

Public keys can be exported as standard X.509 SubjectPublicKeyInfo (DER/PEM) or as a one-line string
`SSPK1:<profile>:<base64url>`. Every import checks that the key matches the selected profile: the exact modulus size
for RSA, the exact named curve, and a valid on-curve point for ECC. Keys of a different algorithm, size, or curve,
such as RSA-1024 or P-521, are rejected. A SHA-256 fingerprint computed over a provider-independent encoding of
the key lets users verify a contact's key in person.

### 1.2 Versioned binary format

Each encrypted message is a self-describing envelope with fixed-size fields: magic `SS` (2 B), version (1 B),
profile (1 B), recipient key id (4 B, the start of the key fingerprint), key block (RSA-OAEP output of 256/384 B, or a
33/49 B compressed ephemeral EC point), and the AES-GCM ciphertext followed by its 16 B tag. Because all lengths
follow from the profile, the format has no length fields for an attacker to manipulate. The header and key block are
authenticated as GCM associated data and are also bound into the HKDF input. Decryption validates the fields in a
fixed order and returns a controlled error code, such as `TRUNCATED_ENVELOPE`, `WRONG_RECIPIENT`, or
`AUTHENTICATION_FAILED`. An RSA padding failure and a GCM tag failure report the same code, so the error gives no
padding oracle. Converting the envelope to SMS text and splitting it into parts is handled by the Android layer.

### 1.3 Functional testing

80 automated JUnit tests (Gradle) cover the following:
- Round trips with empty, short, 160-character, long (~13 KB), and Unicode messages, including emoji and RTL
  scripts, plus binary data and the 64 KiB maximum.
- Randomized encryption.
- Rejection of a wrong recipient key and of a wrong private key whose key id matches.
- Every single-bit modification of the envelope, at bit 0 and bit 7 of each byte, and every possible truncation.
- Appended bytes, bad magic, bad version, unknown profile, invalid ephemeral points, and mismatched key profiles.
- Lossless export and import of keys.
- HKDF against the official RFC 5869 test vectors, and the hard-coded curve constants against the JCA.
- The exact RSA-OAEP parameters agreed with the Android implementation (SHA-256 digest, MGF1 with SHA-1): an independent cipher with those parameters interoperates with the library in both directions, and one using MGF1-SHA-256 is rejected.

All 80 tests pass. In addition, 56 integration vectors were generated for the Android application. They contain
test keys and valid and invalid envelopes with expected results, so the phone can be checked against the PC
reference.

### 1.4 Performance measurement protocol

The benchmark runner measures four things separately: (i) key-pair generation, (ii) the asymmetric operation
alone (encrypt side and decrypt side), (iii) full encryption, and (iv) full decryption. Full encryption and
decryption are timed for messages of 0, 16, 70, 140, 160, 320, and 1000 ASCII characters, with deterministic
content from a fixed seed. Each operation was timed individually with `System.nanoTime()`. The setup was:
- A global JIT warm-up of 1,500 encrypt/decrypt rounds per profile, followed by a per-operation warm-up.
- n = 50 runs for key generation and n = 500 runs for every other operation.

The runner reports the mean, median, sample standard deviation, minimum, maximum, and 95th percentile. The
tables use the mean, median, and standard deviation. SMS transport time is excluded on purpose. Ciphertext size,
expansion (envelope bytes ÷ plaintext bytes), and SMS segment counts are computed per message size. Segment
counts are estimated for Base64 text over GSM-7 (160 characters, or 153 per part when concatenated) and for raw
8-bit data (140 / 134 bytes). The same runner can be executed on Android as an instrumented test.

**Test environment (PC).** Linux 6.18 x86-64 virtual machine, Intel Xeon @ 2.10 GHz, 4 vCPUs, OpenJDK 64-Bit
Server VM 21.0.13, default SunJCE/SunEC/SunRsaSign providers. The machine is a shared cloud VM, so
absolute times carry some noise; the ratios between profiles are the meaningful result.

## 2. Results

**Table 1. Key generation and asymmetric operation, PC JVM (ms).** Mean ± SD, with the median in parentheses.

| Profile | Security | Key generation | Asymmetric part of encryption | Asymmetric part of decryption |
|---|---:|---:|---:|---:|
| RSA-2048 | 112 | 77.3 ± 45.7 (65.4) | 0.046 (0.039) | 0.916 (0.902) |
| RSA-3072 | 128 | 370.5 ± 210.4 (327.5) | 0.078 (0.072) | 2.593 (2.540) |
| ECC P-256 | 128 | 0.149 ± 0.009 (0.147) | 0.934 (0.823) | 0.958 (0.918) |
| ECC P-384 | 192 | 1.027 ± 0.034 (1.015) | 2.890 (2.827) | 2.031 (2.000) |

**Table 2. Full encryption and decryption of a 160-character message, PC JVM (ms).** Mean ± SD, with the median
in parentheses.

| Profile | Full encryption | Full decryption |
|---|---:|---:|
| RSA-2048 | 0.044 ± 0.005 (0.042) | 0.811 ± 0.034 (0.805) |
| RSA-3072 | 0.091 ± 0.027 (0.080) | 2.500 ± 0.374 (2.427) |
| ECC P-256 | 0.986 ± 0.065 (0.977) | 0.938 ± 0.087 (0.916) |
| ECC P-384 | 3.053 ± 0.321 (2.980) | 2.082 ± 0.159 (2.031) |

The complete tables for all seven message lengths are in `results/report/tables.md`. Over that range, from
empty to 1000 characters, the full encryption and decryption times change by less than the run-to-run
variation, which is about ±0.1 ms for RSA-2048 and P-256 and up to ±0.5 ms for RSA-3072 and P-384.

**Table 3. Ciphertext size and SMS cost of a 160-character message.** One unencrypted SMS.

| Profile | Fixed overhead | Envelope | Expansion | Base64 characters | SMS (Base64, GSM-7) | SMS (8-bit binary) |
|---|---:|---:|---:|---:|---:|---:|
| RSA-2048 | 280 B | 440 B | 2.75× | 588 | 4 | 4 |
| RSA-3072 | 408 B | 568 B | 3.55× | 760 | 5 | 5 |
| ECC P-256 | 57 B | 217 B | 1.36× | 292 | 2 | 2 |
| ECC P-384 | 73 B | 233 B | 1.46× | 312 | 3 | 2 |

For a 16-character message, the expansion is 18.5× for RSA-2048, 26.5× for RSA-3072, 4.6× for P-256, and
5.6× for P-384. Both ECC profiles still fit in a single SMS, while RSA needs 3 to 4 SMS.

Figures:
- Fig. 1: key generation (log scale).
- Fig. 2: asymmetric operations.
- Fig. 3: full encryption and decryption versus message length.
- Fig. 4: ciphertext size and SMS segments.
- Fig. 5: security strength versus decryption cost.

## 3. Discussion

**Compare equal security, not equal key length.** A 3072-bit RSA key and a 256-bit P-256 key both give about 128
bits of security [5]. RSA-2048 gives only 112 bits, and P-384 gives 192 bits. A comparison that pairs "RSA-2048 vs
ECC-256" or that ranks profiles by key length in bits would therefore be misleading. The main comparison below
is RSA-3072 versus P-256.

**Key generation.** This is where ECC wins most clearly. P-256 generates a key pair in about 0.15 ms, about 2,500
times faster than RSA-3072 (≈ 370 ms). RSA key generation is also highly variable (SD ≈ 57% of the mean), because
it searches for random primes. The RSA cost grows quickly with size: RSA-3072 takes about 4.8 times as long as
RSA-2048. This agrees with the qualitative conclusion of [2] and [3] that ECC key generation is cheaper at equal
security. However, our absolute numbers are 2 to 5 orders of magnitude lower. [1] reports 2,041 ms for RSA-2048
and 6,010 ms for RSA-3072, and [2] reports 76 s for RSA-1024 on a 219 MHz J2ME phone. The difference comes from
modern hardware and the optimized big-integer arithmetic of current JVMs. [2] also reports EC key generation as
*slower* than RSA, which we do not observe; this most likely reflects their implementation rather than the
algorithm.

**Encryption versus decryption.** The two families show opposite profiles. RSA public-key operations use the
small exponent 65537, so RSA encryption is the fastest operation measured, at 0.04 to 0.08 ms. ECIES encryption
must generate an ephemeral key and perform a full scalar multiplication, which takes about 1 ms on P-256 and
3 ms on P-384. On the decryption side, P-256 (0.92 ms) is about 2.7 times faster than RSA-3072 (2.4–2.5 ms) at the
same security level. These results match the remark in [3] that "public key operations are faster with RSA".
They contradict its overall claim that ECC encryption is faster. In [3], RSA was used block by block on whole files,
while in a hybrid design the asymmetric step is done once per message. For an SMS application both directions
take only a few milliseconds and are not noticeable to the user. The main performance differences are therefore
key generation and message size.

**Message length.** In a hybrid design the message length has almost no effect on time, because the text is
processed by AES-GCM (microseconds per kilobyte) and the asymmetric operation runs once per message. This
removes the block-allocation problem described in [1]. There, textbook RSA can only encrypt up to the modulus
size per block (256 characters for 2048 bits), and longer texts must be split and encrypted block by block.

**Size and SMS cost.** For SMS, ciphertext size matters more than CPU time. The fixed overhead is 57 B for P-256
versus 408 B for RSA-3072, because an RSA-OAEP block is as large as the modulus while a compressed EC point is
only 33 bytes. A typical 160-character message therefore needs 2 SMS with P-256 but 5 with RSA-3072, compared
with 1 SMS unencrypted. RSA-3072 takes 4 SMS even for an empty message. This confirms the conclusion of [2] that
ECC is the more cost-effective choice for SMS. It also matches the observation in [1] that 2048-bit RSA output
(344 Base64 characters per block) does not fit in a 160-character SMS. However, we disagree with [1]'s remedy of
using 512-bit RSA keys. RSA-512 was publicly factored in 1999 and offers no meaningful security today. [3] reports
that ECC "does not change the file size". That cannot hold for an authenticated encryption scheme: our ECC
envelopes are always 57–73 bytes larger than the plaintext. That fixed cost is small, but it exists, and it buys
integrity and authenticity of the ciphertext.

**Differences from the historical articles.** Our implementation is a modern hybrid design, not a reproduction of
references [1]–[3]:
- [1] uses textbook RSA, with no padding, block splitting, and Base64. Textbook RSA is deterministic and malleable,
  and it provides no integrity.
- [2] evaluates RSA, ElGamal, and an unspecified elliptic-curve encryption on J2ME with key sizes up to 1024 bits.
- [3] compares RSA-1024 with 160-bit ECC. Both sizes are below the 112-bit minimum that NIST has required since
  2014 [5].

Our design differs in five ways. It uses OAEP padding instead of raw RSA, a key-derivation function, and
authenticated encryption (GCM), so any modification is detected. It authenticates the header metadata and uses a
versioned binary format. It validates every imported key against the selected profile, and it adds fingerprints for
manual key verification. Only the current NIST-approved sizes (≥ 112 bits) are offered. Because the design and
the platforms differ, our times should be compared with the articles only in relative terms, as trends and ratios.

**Limitations.** The PC results come from a shared virtual machine. The Android results that Bruno is collecting
with the same runner and the quick configuration will show the behaviour on real mobile hardware, where we
expect larger absolute times but similar ratios. Segment counts are estimates from the 3GPP limits; the real
count is the one reported by Android's `SmsManager`. On the security side:
- The scheme provides confidentiality and integrity, but it does not authenticate the sender. Anyone who knows
  the recipient's public key can create a valid message. A signature, such as ECDSA, or a static-static key
  agreement would add sender authentication.
- There is no replay protection.
- Compromise of the recipient's long-term private key exposes all past messages, so the scheme has no forward
  secrecy.
- SMS metadata (phone numbers, time, and approximate length) remains visible to the operator.
- Both RSA and ECC would be broken by a large-scale quantum computer.

## References (for this section)

[1]–[3] as in the course references. Additional:
[5] E. Barker, "Recommendation for Key Management, Part 1: General," NIST SP 800-57 Part 1 Rev. 5, 2020.
[6] M. Dworkin, "Recommendation for Block Cipher Modes of Operation: Galois/Counter Mode (GCM) and GMAC," NIST SP 800-38D, 2007.
[7] H. Krawczyk, P. Eronen, "HMAC-based Extract-and-Expand Key Derivation Function (HKDF)," RFC 5869, 2010.
[8] K. Moriarty et al., "PKCS #1: RSA Cryptography Specifications Version 2.2," RFC 8017, 2016.
[9] SECG, "SEC 1: Elliptic Curve Cryptography," version 2.0, 2009.
