@echo off
echo Opening firewall port 8080...
netsh advfirewall firewall add rule name="ERP-8080" dir=in action=allow protocol=TCP localport=8080
echo Opening firewall port 5432 (PostgreSQL)...
netsh advfirewall firewall add rule name="ERP-PG-5432" dir=in action=allow protocol=TCP localport=5432
echo Done! Port 8080 is now accessible from internet.
echo.
echo Test: Open http://114.55.15.199:8080 in your browser
pause
