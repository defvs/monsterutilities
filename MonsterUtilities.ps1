$java8 = Get-ItemProperty "Registry::HKEY_LOCAL_MACHINE\SOFTWARE\JavaSoft\Java Development Kit\1.8"
$javaHome = $java8.JavaHome
$jar = ls | %{$_.Name} | where {$_ -match "MonsterUtilities.*.jar"}
& "$javaHome\bin\java" -jar "$jar"