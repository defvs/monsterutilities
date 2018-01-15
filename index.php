<?php

//var_dump(get_defined_vars());
$file = $_FILES['log'];

$subject = $_POST['subject'];
$message = $_POST['message'];

$folder = time() . " - " . $subject;
mkdir($folder);
file_put_contents($folder . "/message.txt", $message);

if($file['size'] > 1000000) {
	header("HTTP/1.0 413 Request Entity Too Large");
} elseif(move_uploaded_file($file['tmp_name'], $folder . "/" . basename($file['name']))) {
	header("HTTP/1.0 200 OK");
} else {
	header("HTTP/1.0 500 Internal Server Error");
}

?>