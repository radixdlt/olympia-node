#CVE-2020-13844. Used to compile C++
apt remove -y cpp cpp-9

#C routines CVE-2021-27218 CVE-2021-27219 CVE-2021-28153
apt remove -y libglib2.0-0 libglib2.0-bin libglib2.0-data

#CVE-2013-7445 and others (neded to compile drivers)
apt remove -y linux-libc-dev
