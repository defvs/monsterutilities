$java8 = Get-ItemProperty "Registry::HKEY_LOCAL_MACHINE\SOFTWARE\JavaSoft\Java Development Kit\1.8"
$h = $java8.JavaHome
& "$h\bin\java" -jar "MonsterUtilities-1.0.0.jar"