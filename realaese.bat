@echo off
setlocal
chcp 65001 >nul
python scripts\publish_release.py %*
