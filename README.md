# JARVIS — O'zbekcha ovozli yordamchi

Windows uchun o'zbekcha ovozli yordamchi. Mikrofon orqali o'zbekcha nutqni `uz-UZ` tilida taniydi va o'zbekcha javob qaytaradi.

## Tez ishga tushirish

1. Python 3.11 yoki 3.12 o'rnating va o'rnatishda **Add Python to PATH** ni belgilang.
2. `install.bat` ni bir marta ishga tushiring.
3. `run.bat` ni ishga tushiring.
4. Mikrofonga masalan: **"Jarvis, shu yerdamisan?"**, **"soat nechi?"**, **"YouTube'ni och"**, **"internetdan Buxoro ob-havosini qidir"** deb ayting.

## Ovoz

Nutqni tanish: `uz-UZ`.
Javob ovozi: Microsoft neural `uz-UZ-SardorNeural`.
Internet nutqni tanish va neural ovoz uchun kerak.

## Mikrofonsiz test

```bash
python jarvis.py --text "soat nechi" --no-voice
```

## Hozirgi buyruqlar

- Jarvis, shu yerdamisan?
- Salom
- Soat nechi?
- Bugun sana nechanchi?
- YouTube'ni och
- Google'ni och
- GitHubni och
- Internetdan ... qidir
- Protsessor / batareya
- Nimalar qila olasan?
- Jarvisni yop

## Eslatma

Bu versiyada yuz orqali autentifikatsiya ataylab majburiy qilinmagan. Sababi eski loyihadagi yuz modeli boshqa odam uchun o'qitilgan va yangi kompyuterda dasturning ochilishiga xalaqit beradi. Keyinchalik foydalanuvchining o'z yuzi uchun alohida trening qo'shish mumkin.
