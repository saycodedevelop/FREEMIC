import sys
from pathlib import Path

version = sys.argv[1]
text = Path("CHANGELOG.md").read_text(encoding="utf-8")
changes = text.split("## [Unreleased]", 1)[1].split("\n## [", 1)[0].strip()
print(f"""# KaraokeLink v{version}

ไมโครโฟนไร้สายและเครื่องเล่นคาราโอเกะ สำหรับ Android ผ่าน Wi-Fi เดียวกัน

{changes}

## ดาวน์โหลดและติดตั้ง

- ดาวน์โหลด `KaraokeLink-{version}.apk` จากไฟล์แนบด้านล่าง รองรับ Android 9 ขึ้นไป
- ติดตั้ง APK เดียวกันทั้งเครื่องเล่นและเครื่องไมค์
- ถ้ามาจากรุ่น preview เดิม ต้องถอนรุ่นเดิมหนึ่งครั้ง เพราะเปลี่ยนเป็นกุญแจเซ็น release ถาวร หลังจากนี้อัปเดตทับได้

## การตรวจสอบ

ผ่าน Android build/lint, การทดสอบหน้าเล่นเพลงและ foreground microphone service,
รวมถึงการทดสอบ C++ ด้วย sanitizers และการจำลอง jitter/clock drift
ผลเสียงบน Bluetooth และการจัดการแบตเตอรี่ยังต้องตรวจบนมือถือและลำโพงจริง

ไฟล์ `SHA256SUMS.txt` ใช้ตรวจสอบความสมบูรณ์ของ APK
""")