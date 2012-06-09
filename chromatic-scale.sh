#/usr/bin/sh

# Should only be one arg, the port
if [ $# -ne 1 ]; then
  echo 
  echo "USAGE: chromatic-scale.sh [port]"
  echo
  exit 1
fi

URL="http://127.0.0.1:$1/"

# Uncomment second curl to get error messages
curl $URL?tone=C4\&duration=200 >& /dev/null
#curl $URL?tone=C4\&duration=200

# If first note failed, bail with appropriate message.
if [ $? != 0 ]; then
  echo
  echo "Cannot contact server. Are you sure server at $URL is online?"
  echo "Also, make sure curl is installed."
  echo
  exit 1
fi

curl $URL?tone=Db4\&duration=200 >& /dev/null
curl $URL?tone=D4\&duration=200 >& /dev/null
curl $URL?tone=Eb4\&duration=200 >& /dev/null
curl $URL?tone=E4\&duration=200 >& /dev/null
curl $URL?tone=F4\&duration=200 >& /dev/null
curl $URL?tone=Gb4\&duration=200 >& /dev/null
curl $URL?tone=G4\&duration=200 >& /dev/null
curl $URL?tone=Ab4\&duration=200 >& /dev/null
curl $URL?tone=A4\&duration=200 >& /dev/null
curl $URL?tone=Bb4\&duration=200 >& /dev/null
curl $URL?tone=B4\&duration=200 >& /dev/null
curl $URL?tone=C5\&duration=200 >& /dev/null

