#Update curl https://curl.se/docs/CVE-2021-22876.html
apt remove curl -y
apt purge curl
apt update -y
apt install -y unzip wget
apt install -y build-essential libcurl4 openssl libssl-dev libssh-dev zlib1g-dev libbrotli-dev brotli libkrb5-dev libldap2-dev librtmp-dev libpsl-dev libnghttp2-dev
cd /usr/local/src
rm -rf curl*
wget https://curl.se/download/curl-7.76.1.zip
unzip curl-7.76.1.zip
cd curl-7.76.1
./configure --with-ssl --with-zlib --with-gssapi --enable-ldap --enable-ldaps --with-libssh --with-nghttp2
make
make install
curl -V

#CVE-2020-13844. Used to compile C++
apt remove -y cpp cpp-9

#C routines CVE-2021-27218 CVE-2021-27219 CVE-2021-28153
apt remove -y libglib2.0-0 libglib2.0-bin libglib2.0-data

#CVE-2013-7445 and others (neded to compile drivers)
apt remove -y linux-libc-dev

apt remove -y unzip wget
apt autoremove -y
