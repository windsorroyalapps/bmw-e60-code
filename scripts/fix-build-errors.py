#!/usr/bin/env python3
"""
OpenCode Build Error Auto-Fixer
Reads Gradle/Kotlin compilation errors and uses AI to generate fixes.
"""

import json
import os
import re
import sys
import textwrap
from pathlib import Path


def call_ai(prompt: str) -> str | None:
    """Call AI API for code fixes."""
    import urllib.request
    
    openai_key = os.environ.get("OPENAI_API_KEY")
    if openai_key:
        try:
            req = urllib.request.Request(
                "https://api.openai.com/v1/chat/completions",
                data=json.dumps({
                    "model": "gpt-4o-mini",
                    "messages": [
                        {"role": "system", "content": "You are an expert Kotlin/Android developer. Fix compilation errors precisely. Output ONLY the corrected code block."},
                        {"role": "user", "content": prompt}
                    ],
                    "temperature": 0.1,
                    "max_tokens": 2048
                }).encode(),
                headers={"Authorization": f"Bearer {openai_key}", "Content-Type": "application/json"},
                method="POST"
            )
            with urllib.request.urlopen(req, timeout=60) as resp:
                return json.loads(resp.read().decode())["choices"][0]["message"]["content"]
        except Exception as e:
            print(f"  OpenAI error: {e}", file=sys.stderr)
    return None


def extract_code_block(text: str) -> str:
    m = re.search(r"```(?:kotlin)?\s*\n(.*?)\n```", text, re.DOTALL)
    return m.group(1).strip() if m else text.strip()


def parse_build_errors(error_file: str, log_file: str) -> list[dict]:
    """Parse Gradle build output to extract file/line/error info."""
    errors = []
    
    # Pattern: e: file:///path/file.kt:line:col Error message
    pattern = re.compile(
        r"^e:\s*file:///(.*?)\:(\d+)\:(\d+)\s*(.*)$"
    )
    
    seen = set()
    with open(error_file, "r", encoding="utf-8", errors="replace") as f:
        for line in f:
            match = pattern.match(line.strip())
            if match:
                filepath = match.group(1)
                line_num = int(match.group(2))
                col = int(match.group(3))
                message = match.group(4)
                
                key = (filepath, line_num, message)
                if key not in seen:
                    seen.add(key)
                    errors.append({
                        "file": filepath,
                        "line": line_num,
                        "column": col,
                        "message": message,
                    })
    
    return errors


def get_context(filepath: str, line_num: int, radius: int = 8) -> str:
    try:
        with open(filepath, "r", encoding="utf-8") as f:
            lines = f.read().splitlines()
        start = max(0, line_num - radius - 1)
        end = min(len(lines), line_num + radius)
        return "\n".join(f"{i+1:4d}: {lines[i]}" for i in range(start, end))
    except Exception:
        return ""


def apply_fix(filepath: str, line_num: int, new_code: str) -> bool:
    """Apply fix to source file."""
    try:
        with open(filepath, "r", encoding="utf-8") as f:
            lines = f.read().splitlines()
        
        # Simple line replacement strategy
        # Find the function/class containing the error
        func_start = line_num - 1
        while func_start > 0:
            line = lines[func_start]
            if re.match(r"^\s*(?:fun|class|object|interface|val|var)", line):
                break
            func_start -= 1
        
        # Find end of the element
        brace_count = 0
        func_end = func_start
        found_open = False
        for i in range(func_start, len(lines)):
            for ch in lines[i]:
                if ch == "{":
                    brace_count += 1
                    found_open = True
                elif ch == "}":
                    brace_count -= 1
            if found_open and brace_count == 0:
                func_end = i
                break
        
        # Replace the block
        indent = len(lines[func_start]) - len(lines[func_start].lstrip())
        indented = textwrap.indent(new_code, " " * indent)
        new_lines = lines[:func_start] + [indented] + lines[func_end + 1:]
        
        with open(filepath, "w", encoding="utf-8") as f:
            f.write("\n".join(new_lines) + "\n")
        return True
    except Exception as e:
        print(f"  Apply error: {e}", file=sys.stderr)
        return False


def main():
    error_file = sys.argv[1] if len(sys.argv) > 1 else "build-errors.txt"
    log_file = sys.argv[2] if len(sys.argv) > 2 else "build-log.txt"
    
    errors = parse_build_errors(error_file, log_file)
    print(f"Found {len(errors)} unique error(s)")
    
    if not errors:
        print("No parseable errors found. Check build-log.txt manually.")
        sys.exit(0)
    
    fixed = 0
    for idx, err in enumerate(errors, 1):
        filepath = err["file"]
        line_num = err["line"]
        message = err["message"]
        
        # Only fix files in our source tree
        if "app/src/main/java" not in filepath:
            print(f"[{idx}] Skipping external file: {filepath}")
            continue
        
        print(f"[{idx}/{len(errors)}] {filepath}:{line_num}: {message}")
        
        context = get_context(filepath, line_num)
        
        prompt = f"""Fix this Kotlin compilation error:

File: {filepath}
Line: {line_num}
Error: {message}

Context:
```kotlin
{context}
```

Provide ONLY the corrected code for the function/element containing the error. Do not include explanations."""
        
        completion = call_ai(prompt)
        if not completion:
            print("  -> No AI response, skipping.")
            continue
        
        code = extract_code_block(completion)
        
        # Save for review
        review_dir = Path("build-fixes")
        review_dir.mkdir(exist_ok=True)
        review_file = review_dir / f"{Path(filepath).name}_{line_num}.kt"
        review_file.write_text(code, encoding="utf-8")
        
        if apply_fix(filepath, line_num, code):
            print(f"  -> Applied fix to {filepath}")
            fixed += 1
        else:
            print(f"  -> Saved to {review_file} for manual review")
    
    print(f"\nDone: {fixed}/{len(errors)} errors auto-fixed.")
    print(f"Review directory: build-fixes/")


if __name__ == "__main__":
    main()
