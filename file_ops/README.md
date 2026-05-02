# file_ops вЂ” Native File I/O Layer

Strict, sandboxed file operations module for SantiyaLocalAiHub2. All disk I/O in the app goes through this C++ native layer. Kotlin never touches the filesystem directly.

## Purpose

- Enforce strict file permissions (700 dirs, 600 files)
- Jail all paths to the app's data directory (no traversal, no symlinks)
- Provide atomic writes (write в†’ fsync в†’ rename)
- Memory-mapped reads for large files
- File locking for concurrent access safety
- Secure deletion (zero-fill + unlink)

## Architecture

```
app/ (Kotlin)
  в””в”Ђв”Ђ FileOps.kt (thin JNI wrapper)
        в”‚
        в”‚ JNI
        в–ј
file_ops/ (C++)
  в”њв”Ђв”Ђ file_ops.cpp        JNI entry points
  в”њв”Ђв”Ђ io_engine.cpp/h     Core I/O (read, write, mmap, fsync)
  в”њв”Ђв”Ђ path_guard.cpp/h    Path validation & sandboxing
  в””в”Ђв”Ђ CMakeLists.txt
```

## JNI API

| Function | Description |
|---|---|
| `nativeInit(basePath)` | Set data root, create directory structure, set permissions |
| `nativeWrite(path, data, offset)` | Atomic write: write to `.tmp`, fsync, rename over target |
| `nativeRead(path, offset, length)` | Read bytes, mmap-backed for large files |
| `nativeDelete(path)` | Secure delete: zero-fill file contents, then unlink |
| `nativeExists(path)` | Fast `stat()` check |
| `nativeListDir(path)` | List directory entries (files only, no `.` / `..`) |
| `nativeRename(from, to)` | Atomic rename (`rename()` syscall) |
| `nativeGetSize(path)` | Return file size in bytes |
| `nativeFsync(path)` | Force flush to disk |
| `nativeLock(path)` | Acquire exclusive file lock (`flock`) |
| `nativeUnlock(path)` | Release file lock |

## Path Security

All paths are validated in C++ before any I/O:

1. Paths must be **relative** вЂ” joined with the initialized base path
2. `..` components are **rejected** (no directory traversal)
3. Symlinks are **rejected** (`lstat` check, not `stat`)
4. Final resolved path must start with base path prefix
5. Path length capped at 256 characters

```
Base: /data/data/com.santiya.localaihub/files/

Valid:   "ums/chats.ums"     в†’ /data/.../files/ums/chats.ums  вњ“
Invalid: "../shared_prefs"   в†’ rejected (traversal)           вњ—
Invalid: "/etc/passwd"       в†’ rejected (absolute)            вњ—
```

## Permissions

| Resource | Mode | Notes |
|---|---|---|
| Base directory | `0700` | Owner (app) only, set on `nativeInit` |
| Subdirectories | `0700` | Created with `mkdir()` + `chmod()` |
| Data files | `0600` | Set after every write |
| Temp files | `0600` | Used during atomic writes |

## Atomic Write Flow

```
1. Write data to "path.tmp" (new file, 0600)
2. fsync("path.tmp")         вЂ” ensure data on disk
3. rename("path.tmp", "path") вЂ” atomic replace (POSIX guarantee)
4. fsync(parent_directory)    вЂ” ensure directory entry on disk
```

If crash occurs at any point: either old data intact (step 1-2 crash) or new data intact (step 3+ crash). Never partial.

## Dependencies

- Android NDK (C++17)
- POSIX APIs only (no external libraries)
- No dependency on system_encryptor or ums (lowest layer in the stack)

## Module Dependency Graph

```
file_ops  в†ђв”Ђв”Ђ ums (uses for all disk I/O)
          в†ђв”Ђв”Ђ app (future: any direct file needs)
```
