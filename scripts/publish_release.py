#!/usr/bin/env python3
import os
import sys

# Ensure UTF-8 output on Windows console
if sys.stdout.encoding != 'utf-8':
    try:
        sys.stdout.reconfigure(encoding='utf-8')
        sys.stderr.reconfigure(encoding='utf-8')
    except:
        pass

import re
import json
import shutil
import subprocess
import urllib.request
import urllib.error
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
VERSION_CONFIG = ROOT / "iosApp" / "Configuration" / "Version.xcconfig"
LOCAL_PROPS = ROOT / "local.properties"
APK_DIR = ROOT / "androidApp" / "build" / "outputs" / "apk" / "full" / "release"
GITHUB_OWNER = "9000000"
GITHUB_REPO = "NuvioMobileFast"

def read_current_version_and_code():
    version = "0.4.21"
    code = 126
    if VERSION_CONFIG.exists():
        with open(VERSION_CONFIG, "r", encoding="utf-8") as f:
            for line in f:
                if line.startswith("MARKETING_VERSION"):
                    version = line.split("=")[1].strip()
                elif line.startswith("CURRENT_PROJECT_VERSION"):
                    val = line.split("=")[1].strip()
                    if val.isdigit():
                        code = int(val)
    return version, code

def update_version_config(new_version, new_code):
    lines = [
        f"CURRENT_PROJECT_VERSION={new_code}\n",
        f"MARKETING_VERSION={new_version}\n"
    ]
    with open(VERSION_CONFIG, "w", encoding="utf-8", newline="\n") as f:
        f.writelines(lines)
    print(f"   [CONFIG] Da cap nhat {VERSION_CONFIG.name}: VERSION={new_version}, CODE={new_code}")

def read_token():
    token = os.environ.get("GITHUB_TOKEN") or os.environ.get("GH_TOKEN")
    if token:
        return token.strip()
    if LOCAL_PROPS.exists():
        with open(LOCAL_PROPS, "r", encoding="utf-8") as f:
            for line in f:
                line = line.strip()
                if line.startswith("GITHUB_TOKEN=") or line.startswith("GH_TOKEN="):
                    return line.split("=", 1)[1].strip()
    return None

def build_apk():
    print("\n[1/3] Dang dong goi ban Release APK...")
    gradle_cmd = "gradlew.bat" if os.name == "nt" else "./gradlew"
    cmd = [str(ROOT / gradle_cmd), ":androidApp:assembleFullRelease"]
    res = subprocess.run(cmd, cwd=ROOT)
    if res.returncode != 0:
        print("[ERROR] Loi khi bien dich APK Release!")
        sys.exit(res.returncode)
    print("[OK] Dong goi APK Release thanh cong!")

def get_release_apks(version):
    """
    Lay danh sach cac file APK Release cho dung phien ban hien tai.
    Tu dong doi ten cac file con dang mac dinh (androidApp-full-...-release.apk)
    sang NuvioMobile-{version}-{abi}.apk neu chua duoc doi.
    """
    search_dirs = []
    if APK_DIR.exists():
        search_dirs.append(APK_DIR)
    else:
        fallback_dir = ROOT / "androidApp" / "build" / "outputs" / "apk"
        if fallback_dir.exists():
            search_dirs.append(fallback_dir)

    apks = []
    seen = set()
    prefix = f"NuvioMobile-{version}-"
    generic_prefix = "NuvioMobile-"

    for d in search_dirs:
        for p in d.rglob("*.apk"):
            if "unaligned" in p.name.lower():
                continue
            if "release" not in p.parent.name.lower() and "release" not in p.name.lower():
                continue
            name = p.name

            # 1. Truong hop dung file NuvioMobile-{version}-{abi}.apk da duoc dat ten chuan
            if name.startswith(prefix) and name.endswith(".apk"):
                if p not in seen:
                    apks.append(p)
                    seen.add(p)
                continue

            # 2. Neu truoc do la ten mac dinh Gradle kieu androidApp-full-...-release.apk, doi ten sang NuvioMobile-{version}-{abi}.apk
            if "release" in name.lower() and not name.startswith(generic_prefix):
                abi = "universal"
                for target_abi in ["arm64-v8a", "armeabi-v7a", "x86_64", "x86", "universal"]:
                    if target_abi in name.lower():
                        abi = target_abi
                        break
                new_name = f"NuvioMobile-{version}-{abi}.apk"
                new_path = p.parent / new_name
                try:
                    if p != new_path:
                        if new_path.exists():
                            new_path.unlink()
                        p.rename(new_path)
                        print(f"   [RENAME] {p.name} -> {new_name}")
                        p = new_path
                except Exception as e:
                    print(f"   [WARN] Khong the doi ten {p.name}: {e}")
                if p not in seen:
                    apks.append(p)
                    seen.add(p)

    return sorted(apks, key=lambda x: x.name)

def get_current_branch():
    try:
        res = subprocess.run(
            ["git", "rev-parse", "--abbrev-ref", "HEAD"],
            cwd=ROOT,
            capture_output=True,
            text=True,
            encoding="utf-8",
            errors="replace"
        )
        if res.returncode == 0 and res.stdout.strip():
            return res.stdout.strip()
    except Exception:
        pass
    return "tet"

def upload_via_gh(tag, title, notes, apks, is_prerelease=False):
    target_branch = get_current_branch()
    release_type_name = "Pre-release" if is_prerelease else "Release chính thức"
    print(f"\n[3/3] Dang tai len GitHub qua GitHub CLI (gh) vao {GITHUB_OWNER}/{GITHUB_REPO} (nhanh: {target_branch}, loai: {release_type_name})...")
    cmd = [
        "gh", "release", "create", tag,
        *[str(apk) for apk in apks],
        "--repo", f"{GITHUB_OWNER}/{GITHUB_REPO}",
        "--target", target_branch,
        "--title", title,
        "--notes", notes,
    ]
    if is_prerelease:
        cmd.append("--prerelease")
    res = subprocess.run(cmd, cwd=ROOT)
    if res.returncode == 0:
        print(f"\n[SUCCESS] Da phat hanh {release_type_name} {tag} thanh cong tai:")
        print(f"-> https://github.com/{GITHUB_OWNER}/{GITHUB_REPO}/releases/tag/{tag}\n")
        return True
    return False

def upload_via_api(token, tag, title, notes, apks, is_prerelease=False):
    target_branch = get_current_branch()
    release_type_name = "Pre-release" if is_prerelease else "Release chính thức"
    print(f"\n[3/3] Dang tao {release_type_name} va tai len APK qua GitHub API vao {GITHUB_OWNER}/{GITHUB_REPO} (nhanh: {target_branch})...")
    create_url = f"https://api.github.com/repos/{GITHUB_OWNER}/{GITHUB_REPO}/releases"
    payload = json.dumps({
        "tag_name": tag,
        "target_commitish": target_branch,
        "name": title,
        "body": notes,
        "draft": False,
        "prerelease": is_prerelease
    }).encode("utf-8")
    
    headers = {
        "Authorization": f"Bearer {token}",
        "Accept": "application/vnd.github+json",
        "User-Agent": "NuvioMobileFast-Release-Script",
        "Content-Type": "application/json",
    }
    
    upload_url_template = ""
    html_url = ""
    req = urllib.request.Request(create_url, data=payload, headers=headers, method="POST")
    try:
        with urllib.request.urlopen(req) as resp:
            data = json.loads(resp.read().decode("utf-8"))
            upload_url_template = data.get("upload_url", "")
            html_url = data.get("html_url", "")
    except urllib.error.HTTPError as e:
        err_msg = e.read().decode("utf-8", errors="replace")
        if e.code in (301, 302, 307, 308):
            redirect_url = e.headers.get("Location")
            if redirect_url:
                print(f"   [REDIRECT] Chuyen huong API den {redirect_url}...")
                redir_req = urllib.request.Request(redirect_url, data=payload, headers=headers, method="POST")
                try:
                    with urllib.request.urlopen(redir_req) as resp:
                        data = json.loads(resp.read().decode("utf-8"))
                        upload_url_template = data.get("upload_url", "")
                        html_url = data.get("html_url", "")
                except Exception as redir_err:
                    print(f"[ERROR] Loi sau chuyen huong: {redir_err}")
                    return False
        elif e.code == 422:
            print(f"   [INFO] Release {tag} da ton tai, dang lay thong tin upload...")
            try:
                get_url = f"https://api.github.com/repos/{GITHUB_OWNER}/{GITHUB_REPO}/releases/tags/{tag}"
                get_req = urllib.request.Request(get_url, headers=headers, method="GET")
                with urllib.request.urlopen(get_req) as get_resp:
                    data = json.loads(get_resp.read().decode("utf-8"))
                    upload_url_template = data.get("upload_url", "")
                    html_url = data.get("html_url", "")
            except Exception as get_err:
                print(f"[ERROR] Khong the lay thong tin release {tag}: {get_err}")
                return False
        else:
            print(f"[ERROR] Loi tao release qua GitHub API: HTTP {e.code} - {err_msg}")
            return False
    
    upload_url_base = upload_url_template.split("{")[0]
    
    for apk in apks:
        size_mb = apk.stat().st_size / (1024 * 1024)
        print(f"   [UPLOAD] Dang tai len {apk.name} ({size_mb:.2f} MB)...")
        with open(apk, "rb") as f:
            apk_bytes = f.read()
        
        target_url = f"{upload_url_base}?name={apk.name}"
        up_headers = {
            "Authorization": f"Bearer {token}",
            "Accept": "application/vnd.github+json",
            "User-Agent": "NuvioMobileFast-Release-Script",
            "Content-Type": "application/vnd.android.package-archive",
            "Content-Length": str(len(apk_bytes)),
        }
        up_req = urllib.request.Request(target_url, data=apk_bytes, headers=up_headers, method="POST")
        try:
            with urllib.request.urlopen(up_req) as up_resp:
                print(f"   [DONE] Da tai xong: {apk.name}")
        except urllib.error.HTTPError as e:
            print(f"   [FAIL] Loi tai len {apk.name}: HTTP {e.code} - {e.read().decode('utf-8')}")
            return False
    
    print(f"\n[SUCCESS] Da phat hanh Release {tag} thanh cong tai:")
    print(f"-> {html_url}\n")
    return True


def prompt_version(current_version):
    # Check if passed as positional argument (skip flags starting with -)
    for arg in sys.argv[1:]:
        if not arg.startswith("-"):
            arg_val = arg.strip().lstrip("vV")
            if arg_val:
                return arg_val
            break
    
    print(f"[*] Phien ban hien tai trong du an: {current_version}")
    try:
        user_input = input(f"[*] Nhap phien ban muon phat hanh (Enter de dung {current_version}): ").strip()
        if user_input:
            return user_input.lstrip("vV").strip()
    except (EOFError, KeyboardInterrupt):
        print("\n[CANCEL] Da huy thao tac.")
        sys.exit(0)
    return current_version

def prompt_release_type():
    """
    Xac dinh loai ban phat hanh: Release chinh thuc (False) hoac Pre-release (True).
    Ho tro nhan flag tu dong lenh (--prerelease, --pre, -p hoac --release, -r).
    Neu khong truyen flag, hoi nguoi dung tuong tac qua console.
    """
    args = [a.lower() for a in sys.argv[1:]]
    if any(a in ("--prerelease", "--pre-release", "--pre", "-p") for a in args):
        return True
    if any(a in ("--release", "--full", "-r") for a in args):
        return False

    if sys.stdin.isatty():
        try:
            print("\n[*] Chon loai ban phat hanh:")
            print("    1. Release chinh thuc (mac dinh)")
            print("    2. Pre-release (ban thu nghiem / beta)")
            ans = input("[*] Nhap lua chon [1/2] (Enter mac dinh la 1): ").strip().lower()
            if ans in ("2", "pre", "prerelease", "p"):
                return True
        except (EOFError, KeyboardInterrupt):
            print("\n[CANCEL] Da huy thao tac.")
            sys.exit(0)
    return False

def get_previous_tag(exclude_tag=None):
    res = subprocess.run(
        ["git", "tag", "-l", "--sort=-v:refname"],
        cwd=ROOT,
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="replace"
    )
    if res.returncode == 0 and res.stdout.strip():
        tags = [t.strip() for t in res.stdout.splitlines() if t.strip()]
        for t in tags:
            if exclude_tag and t.lower() == exclude_tag.lower():
                continue
            mb = subprocess.run(
                ["git", "merge-base", "HEAD", t],
                cwd=ROOT,
                capture_output=True,
                text=True
            )
            if mb.returncode == 0 and mb.stdout.strip():
                return t
    return None

def generate_release_notes_from_git(version, tag, prev_tag=None):
    cmd = ["git", "log"]
    if prev_tag:
        cmd.append(f"{prev_tag}..HEAD")
    else:
        cmd.extend(["-n", "30"])
    cmd.append("--pretty=format:%h%x1f%s%x1f%b%x1e")

    res = subprocess.run(
        cmd,
        cwd=ROOT,
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="replace"
    )

    commits = []
    if res.returncode == 0 and res.stdout.strip():
        for item in res.stdout.strip().split("\x1e"):
            if not item.strip():
                continue
            parts = item.strip().split("\x1f")
            sha = parts[0].strip()
            subject = parts[1].strip() if len(parts) > 1 else ""
            body = parts[2].strip() if len(parts) > 2 else ""
            if not subject:
                continue
            # Skip automated merge commits
            if subject.startswith("Merge branch") or subject.startswith("Merge pull request"):
                continue
            commits.append({"sha": sha, "subject": subject, "body": body})

    # Fallback if no commits between prev_tag and HEAD
    if not commits and prev_tag:
        fb_cmd = ["git", "log", "-n", "10", "--pretty=format:%h%x1f%s%x1f%b%x1e"]
        fb_res = subprocess.run(
            fb_cmd,
            cwd=ROOT,
            capture_output=True,
            text=True,
            encoding="utf-8",
            errors="replace"
        )
        if fb_res.returncode == 0 and fb_res.stdout.strip():
            for item in fb_res.stdout.strip().split("\x1e"):
                if not item.strip():
                    continue
                parts = item.strip().split("\x1f")
                sha = parts[0].strip()
                subject = parts[1].strip() if len(parts) > 1 else ""
                if subject and not (subject.startswith("Merge branch") or subject.startswith("Merge pull request")):
                    commits.append({"sha": sha, "subject": subject, "body": ""})

    if not commits:
        return f"## Nuvio Mobile {version}\n\n- Bản cập nhật bảo trì và sửa lỗi ổn định hệ thống."

    features = []
    fixes = []
    perf = []
    improvements = []
    others = []

    for c in commits:
        subj = c["subject"]
        sha = c["sha"]
        lower = subj.lower()

        if lower.startswith("feat:") or lower.startswith("feat(") or lower.startswith("feature:"):
            clean = re.sub(r"^(feat|feature)(\([^)]+\))?:\s*", "", subj, flags=re.IGNORECASE)
            features.append(f"- {clean[:1].upper() + clean[1:]} ({sha})")
        elif lower.startswith("fix:") or lower.startswith("fix(") or lower.startswith("bugfix:"):
            clean = re.sub(r"^(fix|bugfix)(\([^)]+\))?:\s*", "", subj, flags=re.IGNORECASE)
            fixes.append(f"- {clean[:1].upper() + clean[1:]} ({sha})")
        elif lower.startswith("perf:") or lower.startswith("perf(") or lower.startswith("optimize:"):
            clean = re.sub(r"^(perf|optimize)(\([^)]+\))?:\s*", "", subj, flags=re.IGNORECASE)
            perf.append(f"- {clean[:1].upper() + clean[1:]} ({sha})")
        elif any(lower.startswith(p) for p in ["refactor:", "refactor(", "style:", "style(", "ui:", "ui(", "chore:", "chore("]):
            clean = re.sub(r"^(refactor|style|ui|chore)(\([^)]+\))?:\s*", "", subj, flags=re.IGNORECASE)
            improvements.append(f"- {clean[:1].upper() + clean[1:]} ({sha})")
        else:
            others.append(f"- {subj} ({sha})")

    sections = [f"## Nuvio Mobile {version}"]
    if features:
        sections.append("### ✨ Tính năng mới (Features)\n" + "\n".join(features))
    if fixes:
        sections.append("### 🐛 Sửa lỗi (Bug Fixes)\n" + "\n".join(fixes))
    if perf:
        sections.append("### ⚡ Hiệu năng & Tối ưu (Performance)\n" + "\n".join(perf))
    if improvements:
        sections.append("### ♻️ Cải tiến mã nguồn (Improvements)\n" + "\n".join(improvements))
    if others:
        if features or fixes or perf or improvements:
            sections.append("### 🚀 Thay đổi khác (Other Changes)\n" + "\n".join(others))
        else:
            sections.append("### 🚀 Có gì mới trong bản phát hành này:\n" + "\n".join(others))

    if prev_tag and GITHUB_OWNER and GITHUB_REPO:
        sections.append(f"**Full Changelog**: https://github.com/{GITHUB_OWNER}/{GITHUB_REPO}/compare/{prev_tag}...{tag}")

    return "\n\n".join(sections).strip()

def prompt_release_notes(version, tag, prev_tag=None):
    # If passed as second non-flag argument
    non_flag_args = [a for a in sys.argv[1:] if not a.startswith("-")]
    if len(non_flag_args) > 1 and non_flag_args[1].strip():
        return non_flag_args[1].strip()

    # Generate notes from git commit history
    git_notes = generate_release_notes_from_git(version, tag, prev_tag)

    print("\n--------------------------------------------------")
    print(f"[*] Ghi chú phát hành tự động từ Git commits" + (f" ({prev_tag} -> HEAD):" if prev_tag else ":"))
    print("--------------------------------------------------")
    print(git_notes)
    print("--------------------------------------------------")

    # If running interactively, ask if user wants custom notes
    if sys.stdin.isatty():
        try:
            ans = input("[*] Nhấn Enter để dùng ghi chú trên, hoặc nhập 'e' để tùy chỉnh: ").strip().lower()
            if ans in ("e", "edit", "c", "custom", "y", "yes"):
                print("[*] Nhập nội dung ghi chú (gõ xong nhấn Enter):")
                custom = input("> ").strip()
                if custom:
                    return f"## Nuvio Mobile {version}\n\n{custom}"
        except (EOFError, KeyboardInterrupt):
            pass
    return git_notes

def main():
    if any(a in ("--help", "-h") for a in sys.argv[1:]):
        print("""
Cú pháp sử dụng:
  release.bat [phiên_bản] [ghi_chú] [tùy_chọn]

Tùy chọn:
  --release, -r             Tạo bản Release chính thức (Official Release - mặc định)
  --prerelease, --pre, -p   Tạo bản Pre-release (Bản thử nghiệm / Beta)
  --skip-build, -s          Bỏ qua bước đóng gói APK (sử dụng APK đã build sẵn)
  --help, -h                Hiển thị hướng dẫn này

Ví dụ:
  release.bat                       # Tương tác từng bước qua màn hình console
  release.bat --pre                 # Đánh dấu là Pre-release
  release.bat 0.5.6                 # Chỉ định phiên bản 0.5.6 (Release chính thức)
  release.bat 0.5.6 --pre -s        # Pre-release 0.5.6 và dùng APK có sẵn
""")
        sys.exit(0)

    current_version, current_code = read_current_version_and_code()
    
    # 1. Get version from user
    chosen_version = prompt_version(current_version)

    # 2. Get release type (Release or Pre-release)
    is_prerelease = prompt_release_type()
    release_type_name = "Pre-release" if is_prerelease else "Release chính thức"
    
    # 3. Update Version.xcconfig if version changed
    if chosen_version != current_version:
        new_code = current_code + 1
        update_version_config(chosen_version, new_code)
    else:
        new_code = current_code

    tag = f"v{chosen_version}"
    title = f"Nuvio Mobile {chosen_version}"
    if is_prerelease:
        title += " (Pre-release)"
    prev_tag = get_previous_tag(exclude_tag=tag)
    notes = prompt_release_notes(chosen_version, tag, prev_tag)

    print(f"\n==================================================")
    print(f"Nuvio Mobile - Phat hanh {release_type_name}")
    print(f"Phien ban: {tag} (Code: {new_code})")
    print(f"Loai ban:  {release_type_name}")
    print(f"Tieu de:   {title}")
    print(f"==================================================")

    # 4. Build
    skip_build = "--skip-build" in sys.argv or "-s" in sys.argv
    existing_apks = get_release_apks(chosen_version)
    if existing_apks and not skip_build:
        print(f"\n[*] Phat hien {len(existing_apks)} tep APK da duoc dong goi san cho ban {chosen_version}.")
        if sys.stdin.isatty():
            try:
                ans = input("[*] Ban co muon dung lai cac tep APK nay (bo qua build ~7 phut)? (Y/n): ").strip().lower()
                if ans not in ("n", "no"):
                    skip_build = True
            except (EOFError, KeyboardInterrupt):
                pass
        else:
            skip_build = True

    if not skip_build:
        build_apk()
    else:
        print("   [SKIP] Bo qua buoc dong goi APK, su dung tep APK san co...")

    # 5. Collect APKs
    apks = get_release_apks(chosen_version)
    if not apks:
        print("[ERROR] Khong tim thay file APK nao trong build output!")
        sys.exit(1)

    print(f"\n[2/3] Danh sach file APK Release ({len(apks)} tep):")
    for a in apks:
        print(f"   - {a.name} ({a.stat().st_size / (1024*1024):.2f} MB)")

    # 6. Upload
    gh_available = shutil.which("gh") is not None
    if gh_available:
        if upload_via_gh(tag, title, notes, apks, is_prerelease=is_prerelease):
            return

    token = read_token()
    if token:
        if upload_via_api(token, tag, title, notes, apks, is_prerelease=is_prerelease):
            return
    else:
        print("\n[WARN] Chua cau hinh xac thuc GitHub!")
        print("Them Token vao file local.properties:")
        print("   GITHUB_TOKEN=ghp_your_personal_access_token")
        sys.exit(1)

if __name__ == "__main__":
    main()
