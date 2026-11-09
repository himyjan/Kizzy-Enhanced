<div align="center">
  <img width="2048" height="989" alt="17831909936168749184217530060427" src="https://github.com/user-attachments/assets/7a1b7c4d-3610-4541-b400-8a8c7ef91605" />
<h1><img src="https://github.com/user-attachments/assets/02086040-660d-42d8-8830-78053e1e05a4" height="30" style="vertical-align: middle;"> Kizzy Enhanced</h1>
<h4>ฟอร์กที่ปรับปรุงแล้วของ <a href="https://github.com/dead8309/Kizzy">Kizzy</a> — ตัวจัดการ Discord Rich Presence สำหรับ Android เขียนด้วยภาษา Kotlin</h4>
</div>

<div align="center">
<img src="https://img.shields.io/badge/Minimum%20SDK-27-%23?&style=flat-square&color=5b5ef7">
<img src="https://img.shields.io/badge/kotlin-5b5ef7.svg?logo=kotlin&logoColor=white&style=flat-square">
<img src="https://img.shields.io/badge/package-com.my.kizzy.enhanced-5b5ef7?style=flat-square">
<img src="https://img.shields.io/badge/name-Kizzy%20Enhanced-5b5ef7?style=flat-square">
<div align="center">

[![README in English](https://img.shields.io/badge/README-in%20English-blue?style=for-the-badge)](README.md)
[![README ภาษาไทย](https://img.shields.io/badge/README-ภาษาไทย-blue?style=for-the-badge)](README_TH.md)

</div>
</div>
<p>
<img src="https://github.com/user-attachments/assets/f8402300-d628-4104-8f86-dc6021c9fbbe" width=60%/>
</p>
</div>

> [!NOTE]
> **Kizzy Enhanced** เป็นฟอร์กส่วนตัวของ [Kizzy](https://github.com/dead8309/Kizzy) โดย dead8309 ฟีเจอร์เฉพาะของฟอร์กนี้จะมีเครื่องหมาย ⭐ กำกับไว้ด้านล่าง

## คุณสมบัติ

- [x] ปุ่มที่คลิกได้
- [x] ตรวจจับแอปพลิเคชันที่กำลังทำงานอยู่
- [x] ตรวจจับสื่อที่กำลังเล่นอยู่
- [x] การประทับเวลา (Timestamp) แบบเสริม
- [x] สถานะกำหนดเอง
- [x] บันทึก/โหลดการกำหนดค่าสถานะ
- [x] ธีม Material You
- [x] การแปลภาษา
- [x] ตั้ง่าง่าย
- [x] สปูฟ Console RPC
- [x] สร้างการกำหนดค่าแบบกำหนดเองด้วยรูปภาพและลิงก์ของคุณเอง
- [x] ดูตัวอย่าง RPC ภายในแอปพลิเคชัน
- [x] ทำงานในพื้นหลังแม้ในขณะที่ปิดหน้าจอ
- [x] รองรับ GIF
- [x] รองรับ URL ภายนอก (หมายความว่าคุณสามารถระบุ URL ที่ชี้ไปยังรูปภาพบนเว็บและ Discord จะแสดงผลได้!)
- [x] ใช้รูปภาพจากแกลเลอรี
- [x] ⭐ Spoofer แพลตฟอร์ม (Desktop / Web / Android / iOS / Xbox / Playstation / VR)
- [x] ⭐ ID แอปพลิเคชันกำหนดเอง — ใช้ไอคอนและชื่อแอป Discord ของคุณเอง
- [x] ⭐ ตัวแปรเทมเพลตเสริม (`{{media_album}}`, `{{battery}}`, `{{playback}}` และอื่นๆ)
- [x] ⭐ ตัวเลือกโหมด timestamp (ค่าเริ่มต้นสื่อ/แอป · เวลาปัจจุบันของวัน · มิลลิวินาทีแบบกำหนดเอง)
- [x] ⭐ ออกแบบหน้าจอหลักใหม่ — การ์ดเต็มความกว้างพร้อมสวิตช์เปิด/ปิด
- [x] ⭐ เชื่อมต่อเกตเวย์อัตโนมัติพร้อม Exponential Backoff (ไม่หลุดการเชื่อมต่อหลังผ่านไป 1-2 ชั่วโมงอีกต่อไป)
- [x] ⭐ วงจรการตรวจจับที่ไม่พังง่าย — ข้อผิดพลาดของเครือข่ายจะไม่หยุดการตรวจจับแอป/เกมอีกต่อไป
- [x] ⭐ การตรวจสอบสลับแอปทุก 2 วินาที (จากเดิม 5 วินาที) เพื่อการตรวจจับเกมที่รวดเร็วยิ่งขึ้น

## มีอะไรใหม่ในฟอร์กนี้

การเพิ่มทั้งหมดอยู่ในหน้าจอ **Experimental RPC** และหน้าจอหลัก

### ⭐ Spoofer แพลตฟอร์ม
- เลือกแพลตฟอร์มที่สถานะของคุณเชื่อมต่อ — **Desktop, Web, Android, iOS, Xbox, Playstation, VR** (พอร์ตมาจากแพลตฟอร์มที่ใช้ใน platformSpoofer ของ Equicord)
- ทำงานในระดับ `IDENTIFY` ของเกตเวย์ (สถานะไคลเอนต์) จึงทำให้ปลั๊กอินอย่าง **platformindicators** ตรวจจับได้อย่างถูกต้อง
- นำไปใช้ได้ **ทันที** — บริการจะรีสตาร์ทตัวเองโดยอัตโนมัติ ไม่ต้องสลับด้วยตนเอง

### ⭐ ID แอปพลิเคชันกำหนดเอง (ไอคอนของคุณเอง)
- ตัวเลือก **Built-in / Custom** รูปแบบ Built-in จะใช้ไอคอนเริ่มต้น รูปแบบ Custom จะแสดงช่องให้คุณวาง **Application ID** ของ Discord ของคุณเอง เพื่อให้กิจกรรมแสดงไอคอนและชื่อ**ของคุณ**

### ⭐ ตัวแปรเทมเพลตเสริม
นอกเหนือจากที่มีอยู่เดิม พร้อมการเติมข้อความอัตโนมัติ:
- `{{media_album}}`, `{{media_album_artist}}`, `{{media_duration}}`, `{{media_writer}}`, `{{media_composer}}`
- `{{battery}}` — เปอร์เซ็นต์แบตเตอรี่
- `{{playback}}` — สถานะการเล่น (▶ / ⏸)

### ⭐ ตัวเลือกโหมด timestamp
- **Media / App (ค่าเริ่มต้น)** — การประทับเวลาจากเพลง/แอปปัจจุบัน
- **เช่นเดียวกับเวลาปัจจุบันของวัน** — Discord แสดงเวลาปัจจุบันเป็นเวลาที่ผ่านไป (เช่น เวลา 15:30 น. → แสดง "ผ่านไป 15:30") ตรรกะเดียวกับ customRPC ของ Vencord
- **กำหนดเอง** — พิมพ์ **Timestamp เริ่มต้น / สิ้นสุด (เป็นมิลลิวินาที)** ด้วยตนเอง

### ⭐ ออกแบบหน้าจอหลักใหม่
- การ์ดฟีเจอร์ตอนนี้จัดเรียงเป็นแนวตั้งแบบเต็มความกว้าง
- **สวิตช์เปิด/ปิดบนทุกการ์ด** ทำให้สามารถเริ่มและหยุด Media / Custom / Experimental RPC ได้โดยตรงจากหน้าจอหลัก

### ⭐ ความยืดหยุ่นของเกตเวย์ (พอร์ตจาก [YuuzuTH/Kizzy](https://github.com/YuuzuTH/Kizzy))
- เชื่อมต่ออัตโนมัติพร้อม Exponential Backoff เมื่อเกิดการตัดการเชื่อมต่อที่ไม่คาดคิด
- พยายามกู้คืนเซสชันเมื่อเป็นไปได้ และกลับไปเชื่อมต่อใหม่แบบสดๆ หากล้มเหลว 3 ครั้ง
- รหัสปิดการเชื่อมต่อที่ร้ายแรง (4004, 4010–4014) ยังคงหยุดถาวรตามปกติ
- จำกัดเวลา 60 วินาทีสำหรับการรออัปเดตสถานะ (จากเดิมที่เป็นลูปไม่สิ้นสุด)
- วงจรการตรวจจับถูกครอบด้วย try/catch แล้ว ข้อผิดพลาดของเครือข่ายเพียงครั้งเดียวจึงไม่ทำให้การตรวจจับการสลับแอปหยุดทำงานจนกว่าจะรีสตาร์ทด้วยมือ
- ความถี่การตรวจสอบสลับแอป: **2 วินาที** (จากเดิม 5 วินาที)
- แก้ไข: การสลับระหว่างสองแอปที่เปิดใช้งานอยู่จะอัปเดตสถานะทันที

### การแก้ไขข้อบกพร่อง
- แก้ไขข้อผิดพลาดแอปขัดข้องเมื่อ **"แชร์บันทึก (Share log)"** (สิทธิ์ FileProvider ตอนนี้สอดคล้องกับรหัสแพ็กเกจแล้ว)
- แก้ไขข้อผิดพลาดแอปขัดข้องเมื่อสลับฟีเจอร์ RPC จากหน้าจอหลัก

## ข้อกำหนดของระบบ
- ระบบปฏิบัติการ: Android 8.1 ถึง 16
- RAM: ขั้นต่ำ 3 GB

## ดาวน์โหลด
รับไฟล์ `app-release.apk` ที่ลงลายมือชื่อล่าสุดได้จากหน้า [**Releases**](../../releases/latest)

**Kizzy Enhanced** ใช้รหัสแพ็กเกจ **`com.my.kizzy.enhanced`** จึงสามารถติดตั้งควบคู่ไปกับ Kizzy ตัวดั้งเดิมได้หากคุณมีติดตั้งอยู่แล้ว

> [!WARNING]
> แอปพลิเคชันนี้ใช้การเชื่อมต่อ Discord Gateway โปรดใช้งานด้วยความเสี่ยงของคุณเอง

## การสร้างแอป (Build)
> ข้อกำหนดเบื้องต้น: Android Studio และความคุ้นเคยกับ Gradle, Kotlin และ Jetpack Compose

```console
git clone https://github.com/himyjan/Kizzy-Enhanced.git
```
เปิดโปรเจกต์ใน Android Studio นำเข้า จากนั้น Build และ Run บิลด์รุ่น Release จะถูกสร้างขึ้นโดยอัตโนมัติผ่าน GitHub Actions เวิร์กโฟลว์ **Build Release Apk**

## เครดิต
✨ [Kizzy](https://github.com/dead8309/Kizzy) สำหรับโปรเจกต์ Kizzy ดั้งเดิม

✨ [YuuzuTH/Kizzy](https://github.com/YuuzuTH/Kizzy) สำหรับการแก้ไขความยืดหยุ่นของเกตเวย์

✨ [Read You](https://github.com/Ashinch/ReadYou) และ [Seal](https://github.com/JunkFood02/Seal) สำหรับคอมโพเนนต์ UI

✨ [Material Color Utilities](https://github.com/material-foundation/material-color-utilities)

✨ [Rich-Presence-U](https://github.com/ninstar/Rich-Presence-U) สำหรับข้อมูลเกม Nintendo และ Wii U

✨ [Logra](https://github.com/wingio/Logra) สำหรับ UI ของบันทึก

✨ [Xbox-Rich-Presence-Discord](https://github.com/MrCoolAndroid/Xbox-Rich-Presence-Discord) สำหรับข้อมูลเกม Xbox

✨ [Monet](https://github.com/Kyant0/Monet) สำหรับชุดสี Material3

## สิทธิ์การใช้งาน (License)
**Kizzy** เป็นโครงการโอเพน소스ภายใต้สัญญาอนุญาต GNU GPL 3.0 ซึ่งอนุญาตให้คุณใช้งาน อ้างอิง และแก้ไขซอร์สโค้ดได้ฟรี แต่วิพากษ์วิจารณ์ไม่อนุญาตให้นำโค้ดที่แก้ไขและดัดแปลงไปเผยแพร่หรือจำหน่ายเป็นซอฟต์แวร์เชิงพาณิชย์แบบปิดซอร์ส
