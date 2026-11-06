# File Reader Pro (Android)

A lightweight Android file reader for common text files and binary inspection.

## Features
- Open files through Android's file picker (no broad storage permission required).
- Read and edit text files such as TXT, LUA, JSON, XML, CSV, and LOG.
- UTF-8, UTF-8 BOM, UTF-16 LE/BE, and Windows-1252 fallback decoding.
- Automatic binary detection and HEX + ASCII preview.
- File type and size information.
- Search within text, copy selected/all text, and Save As.
- Supports Android 6.0+; maximum file size is 20 MB.

## Important limitations
- PDF, images, archives, and other binary formats are displayed as HEX, not rendered as documents/media.
- Lua .luac files are identified and shown as HEX. This build does not decompile bytecode to .lua.
- Save As writes UTF-8 text. Do not use it to rewrite binary files.
