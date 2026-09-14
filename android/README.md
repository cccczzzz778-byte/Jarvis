# JARVIS Mobile (Android)

Native Android ilova. Asosiy til: **O‘zbekcha (`uz-UZ`)**.

## V1 imkoniyatlari
- O‘zbekcha ovozni Android SpeechRecognizer orqali tanish
- O‘zbekcha TTS mavjud bo‘lsa ovozli javob
- Google qidiruvi
- YouTube qidiruvi
- Google Maps / xarita qidiruvi
- Kamera ilovasini ochish
- Fayl tanlash
- Telefon raqam terish oynasini ochish
- Vaqt va sanani aytish
- Matnli buyruq kiritish
- Mobil JARVIS animatsiyali interfeys

## APK build
GitHub Actions ichidagi `Build JARVIS Mobile APK` workflow har bir Android o‘zgarishidan keyin debug APK yaratadi. Artifact nomi: `JARVIS-Mobile-APK`.

## Eslatma
Speech-to-text ishlashi telefonning Android/Google speech recognition xizmatiga va internetga bog‘liq bo‘lishi mumkin. `TextToSpeech` uchun `uz-UZ` ovozi telefondagi TTS engine’da bo‘lmasa, javob ekranda ko‘rsatiladi, lekin ovoz chiqarilmasligi mumkin.
