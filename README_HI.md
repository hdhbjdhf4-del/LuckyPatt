# Lua Reader Android

यह Android Studio का शुरुआती प्रोजेक्ट है।

## सुविधाएँ
- Android फ़ाइल picker से .lua सहित फ़ाइल खोलना
- Lua source text पढ़ना और edit करना
- UTF-8 text दिखाना
- Lua bytecode signature (1B 4C 75 61) पहचानना और शुरुआती 4096 bytes का HEX dump दिखाना
- Save As से edited text सेव करना

## .luac के बारे में
Compiled Lua bytecode सामान्य text नहीं होता। यह ऐप उसे पहचानता है और HEX dump दिखाता है। यह पूर्ण decompiler नहीं है और bytecode से original comments/variable names वापस नहीं बना सकता।

APK GitHub Actions से build होगी। Build सफल होने पर Actions run के Artifacts में LuaReader-debug-APK ZIP मिलेगा, जिसके अंदर app-debug.apk होगी।