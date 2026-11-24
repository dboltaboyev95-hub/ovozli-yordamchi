# Ovozli Yordamchi (Android)

Telefonni qo'l tekkizmasdan, ovoz bilan boshqarish: ilovalarni ochish, orqaga/bosh ekran,
aylantirish, tugmalarni bosish, matn yozish, qo'ng'iroq, chiroq, ovoz balandligi.

## APK olish (kompyutersiz, GitHub orqali)
1. github.com da yangi repo yarating (masalan `ovozli-yordamchi`).
2. Shu papkadagi hamma fayllarni (`.github` papkasi bilan birga) repoga yuklang.
3. Repo → **Actions** → "Build APK" tugashini kuting (~5 daqiqa).
4. Ishga tushirish sahifasi → **Artifacts** → `OvozliYordamchi-apk` ni yuklab oling (zip ichida `app-debug.apk`).

## Kompyuterda (Android Studio)
Papkani Android Studio'da oching → **Build → Build APK(s)**.

## Telefonda sozlash
1. APK ni o'rnating (noma'lum manbalarga ruxsat bering).
2. Ilovani oching → **1. Ruxsatlarni berish**.
3. **2. Maxsus imkoniyatni yoqish** → "Ovozli Yordamchi" ni yoqing.
   - Android 13+ da "Cheklangan sozlama" chiqsa: Sozlamalar → Ilovalar → Ovozli Yordamchi →
     yuqori o'ngdagi ⋮ → **Cheklangan sozlamalarga ruxsat berish**, keyin qayta yoqing.
4. Tilni tanlang → **3. Tinglashni boshlash**.
5. Batareya tejash rejimi xizmatni o'chirmasligi uchun: Sozlamalar → Ilovalar → Ovozli Yordamchi →
   Batareya → **Cheklovsiz**.

## Eslatmalar
- Nutqni aniqlash uchun telefonda Google ilovasi bo'lishi kerak. O'zbek tili ishlamasa, Русский yoki English ni tanlang.
- Ba'zi telefonlarda har tinglash boshlanganda qisqa "bip" ovozi chiqadi — bu Google tizimiga bog'liq.
