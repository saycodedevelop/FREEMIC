# KaraokeLink protocol v1

ใช้ IPv4 UDP บน LAN เดียวกัน Port คงที่สำหรับ Host; ใช้ Android NSD (DNS-SD/mDNS) และ UDP broadcast โดยไม่ใช้ Internet signaling

| Channel | Port | Payload |
|---|---:|---|
| Discovery | 40101 | UTF-8 `KL1 DISCOVER`, Host ตอบ unicast `KL1 HOST <model>` |
| Control | 40100 บน Host | UTF-8, ขนาดรับไม่เกิน 512 bytes; Mic bind ephemeral port |
| Audio | 40102 บน Host | Binary fixed 504 bytes |

Host ประกาศบริการ `_karaokelink._udp.` ที่ control port 40100 ส่วน Mic ค้นหาบริการและ resolve IPv4 จากนั้นส่ง `KL1 DISCOVER` แบบ unicast ไปยัง discovery port 40101 ควบคู่กับ broadcast ทุก 1.5 วินาที รายการ Host จะอัปเดตเมื่อได้รับ `KL1 HOST` จริงเท่านั้น จึงไม่แสดงเครื่องจาก NSD cache ที่ติดต่อไม่ได้ Mic ใช้ discovery source port แบบ ephemeral แยกกัน และ Host ตอบกลับไปยัง source port นั้น รุ่นเดิมที่มีเฉพาะ broadcast ยังใช้งานร่วมกันได้

## Control

```text
Mic → Host     KL1 CONNECT <nonce>
Host → Mic     KL1 ACCEPT <same-nonce> <session-id>
Host → Mic     KL1 FULL <same-nonce>       # หากมีไมค์ครบ 2
Mic → Host     KL1 PING <session-id> <monotonic-ms>
Host → Mic     KL1 PONG <session-id> <same-monotonic-ms>
Mic → Host     KL1 BYE <session-id>        # best effort; timeout เป็น fallback
```

CONNECT retry ทุกประมาณ 500 ms สูงสุด 4 s ใช้ nonce เดิม Host ตอบ session เดิมสำหรับ source IP/port/nonce เดิม จึงทน ACCEPT สูญหายได้ PING/PONG ทุก 500 ms, timeout 4 s ใช้ monotonic time ไม่ใช้ wall clock, ไม่วัด one-way latency ด้วยนาฬิกาสองเครื่องที่ไม่ได้ sync

ACCEPT ต้องมาจาก IPv4/port ของ Host ที่เลือกและ nonce ตรงกัน Audio ต้องมี session ตรงกับ Host peer table และ source IP ตรงกัน การกลับ Home/เปลี่ยนขาออก Host ทำให้ session เดิมสิ้นสุด ต้อง reconnect

## Audio header

ตัวเลขทุก field เป็น network byte order (big-endian) **รวม PCM16** เพื่อเลี่ยง struct packing/endianness ระหว่าง ABI

| Offset | Bytes | Field |
|---:|---:|---|
| 0 | 4 | ASCII `KLA1` (protocol magic/version) |
| 4 | 4 | session ID unsigned, nonzero (starter ใช้ช่วง positive Int) |
| 8 | 4 | packet sequence unsigned, wrap modulo 2^32 |
| 12 | 4 | sample-frame timestamp unsigned, +240 ต่อ packet, wrap modulo 2^32 |
| 16 | 4 | sample rate = 48000 |
| 20 | 2 | frames = 240 |
| 22 | 1 | channels = 1 |
| 23 | 1 | format = 1 (PCM16 signed BE) |
| 24 | 480 | 240 mono PCM16 samples |

ปริมาณ payload 100,800 bytes/s ต่อไมค์ (ประมาณ 806.4 kbit/s) ไม่รวม UDP/IP/Wi-Fi overhead ไม่ใช้ codec เริ่มต้นเพื่อลด codec buffering แพ็กเก็ตเล็กกว่า MTU ทั่วไป

Sequence ใช้ป้องกัน duplicate/late packets และเติม silence แบบมีขอบเขต Timestamp เตรียมไว้สำหรับ clock recovery แต่รุ่นนี้ยังไม่ใช้ synchronize ข้ามเครื่อง Unknown format, rate, channel, frame count, size, session หรือ IP ถูกทิ้ง ส่งเสียงแบบ fire-and-forget; ไม่มี ACK/retry สำหรับ audio