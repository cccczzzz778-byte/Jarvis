from __future__ import annotations

import argparse
import asyncio
import datetime as dt
import os
import tempfile
import time
import urllib.parse
import webbrowser
from pathlib import Path

LANGUAGE = "uz-UZ"
VOICE = "uz-UZ-SardorNeural"


def normalize(text: str) -> str:
    return (text or "").lower().replace("ʻ", "'").replace("ʼ", "'").replace("’", "'").strip()


def contains(text: str, *variants: str) -> bool:
    return any(v in text for v in variants)


class Jarvis:
    def __init__(self, voice: bool = True) -> None:
        self.voice_enabled = voice

    async def _edge_speak(self, text: str) -> None:
        import edge_tts
        import pygame

        tmp = Path(tempfile.gettempdir()) / f"jarvis_uz_{os.getpid()}.mp3"
        await edge_tts.Communicate(text=text, voice=VOICE).save(str(tmp))
        try:
            if not pygame.mixer.get_init():
                pygame.mixer.init()
            pygame.mixer.music.load(str(tmp))
            pygame.mixer.music.play()
            while pygame.mixer.music.get_busy():
                time.sleep(0.05)
            pygame.mixer.music.unload()
        finally:
            try:
                tmp.unlink(missing_ok=True)
            except OSError:
                pass

    def speak(self, text: str) -> None:
        print(f"JARVIS: {text}")
        if not self.voice_enabled:
            return
        try:
            asyncio.run(self._edge_speak(text))
        except Exception as exc:
            print(f"[Ovoz xizmati ishlamadi: {exc}]")

    def listen(self) -> str:
        try:
            import speech_recognition as sr
        except ImportError:
            self.speak("SpeechRecognition o'rnatilmagan. Avval install.bat faylini ishga tushiring.")
            return ""

        recognizer = sr.Recognizer()
        try:
            with sr.Microphone() as source:
                print("Tinglayapman...")
                recognizer.dynamic_energy_threshold = True
                recognizer.adjust_for_ambient_noise(source, duration=0.6)
                audio = recognizer.listen(source, timeout=8, phrase_time_limit=15)
        except sr.WaitTimeoutError:
            print("Ovoz eshitilmadi.")
            return ""
        except Exception as exc:
            print(f"Mikrofon xatosi: {exc}")
            return ""

        try:
            text = recognizer.recognize_google(audio, language=LANGUAGE)
            text = normalize(text)
            print(f"SIZ: {text}")
            return text
        except sr.UnknownValueError:
            self.speak("Gapni tushunmadim. Qayta ayting.")
        except sr.RequestError as exc:
            self.speak("Nutqni aniqlash xizmatiga ulanib bo'lmadi.")
            print(f"Google Speech xatosi: {exc}")
        return ""

    def open_url(self, url: str) -> None:
        webbrowser.open_new_tab(url)

    def execute(self, raw_query: str) -> bool:
        q = normalize(raw_query)
        if not q:
            return True

        if contains(q, "jarvis shu yerdamisan", "jarvis bormisan", "jarvis eshityapsanmi"):
            self.speak("Ha, xizmatingizdaman.")
        elif contains(q, "salom", "assalomu alaykum"):
            self.speak("Va alaykum assalom. Sizga qanday yordam bera olaman?")
        elif contains(q, "soat nechi", "vaqt nechi", "vaqtni ayt"):
            now = dt.datetime.now().strftime("%H:%M")
            self.speak(f"Hozir soat {now}.")
        elif contains(q, "bugun sana", "sana nechanchi", "sanani ayt"):
            today = dt.datetime.now().strftime("%d.%m.%Y")
            self.speak(f"Bugungi sana {today}.")
        elif contains(q, "youtube'ni och", "youtubeni och", "yutubni och", "youtube och"):
            self.open_url("https://www.youtube.com")
            self.speak("YouTube ochildi.")
        elif contains(q, "google'ni och", "googleni och", "guglni och", "google och"):
            self.open_url("https://www.google.com")
            self.speak("Google ochildi.")
        elif contains(q, "githubni och", "github och"):
            self.open_url("https://github.com")
            self.speak("GitHub ochildi.")
        elif contains(q, "internetdan qidir", "google'dan qidir", "googledan qidir", "qidirib ber"):
            prefixes = ["internetdan qidir", "google'dan qidir", "googledan qidir", "qidirib ber"]
            term = q
            for p in prefixes:
                term = term.replace(p, "")
            term = term.strip()
            if not term:
                self.speak("Nimani qidiray?")
                term = self.listen()
            if term:
                self.open_url("https://www.google.com/search?q=" + urllib.parse.quote_plus(term))
                self.speak(f"{term} bo'yicha qidiruv natijalarini ochdim.")
        elif contains(q, "protsessor", "cpu", "batareya"):
            try:
                import psutil
                cpu = round(psutil.cpu_percent(interval=0.3))
                battery = psutil.sensors_battery()
                msg = f"Protsessor yuklanishi {cpu} foiz."
                if battery is not None:
                    msg += f" Batareya {round(battery.percent)} foiz."
                self.speak(msg)
            except ImportError:
                self.speak("Tizim ma'lumotlari moduli hali o'rnatilmagan.")
        elif contains(q, "yordam", "nimalar qila olasan", "buyruqlar"):
            self.speak("Men o'zbekcha buyruqlarni tushunaman: vaqt va sanani aytaman, YouTube, Google va GitHubni ochaman, internetdan qidiraman va kompyuter holatini aytaman.")
        elif contains(q, "xayr", "jarvisni yop", "to'xta", "toxta", "dasturni yop"):
            self.speak("Xayr. JARVIS to'xtatildi.")
            return False
        else:
            self.speak("Bu buyruqni hozircha bilmayman.")
        return True

    def run(self) -> None:
        self.speak("Assalomu alaykum. Men JARVISman. O'zbekcha buyruq berishingiz mumkin.")
        while True:
            query = self.listen()
            if query and not self.execute(query):
                break


def main() -> int:
    parser = argparse.ArgumentParser(description="JARVIS Uzbek voice assistant")
    parser.add_argument("--text", help="Mikrofonsiz test uchun buyruq")
    parser.add_argument("--no-voice", action="store_true", help="Ovoz chiqarishni o'chirish")
    args = parser.parse_args()

    bot = Jarvis(voice=not args.no_voice)
    if args.text:
        bot.execute(args.text)
        return 0
    bot.run()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
