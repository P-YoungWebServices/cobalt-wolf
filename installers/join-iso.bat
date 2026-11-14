@echo off
REM Rejoins the OneClick Media Suite ISO parts released on GitHub.
REM Put this file in the same folder as oneclick-mediasuite-ubuntu.iso.part-* and run it.
copy /b oneclick-mediasuite-ubuntu.iso.part-aa+oneclick-mediasuite-ubuntu.iso.part-ab+oneclick-mediasuite-ubuntu.iso.part-ac oneclick-mediasuite-ubuntu.iso
if errorlevel 1 (
  echo.
  echo Join failed - make sure all three .part-* files are in this folder.
  pause
  exit /b 1
)
echo.
echo Done: oneclick-mediasuite-ubuntu.iso rebuilt.
echo Verify it against SHA256SUMS.txt (certutil -hashfile oneclick-mediasuite-ubuntu.iso SHA256)
pause
