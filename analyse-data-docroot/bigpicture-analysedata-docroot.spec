Summary: bigpicture analysedata docroot
Name: bigpicture-analysedata-docroot
Version: 1
Release: 1
License: GPL
Packager: arne.hilmann@gmail.com
Source0: %{name}-%{version}.tar.gz
BuildRoot: %{_tmppath}/%{name}-%{version}-%{release}-root
BuildArch: noarch

%description
bigpicture: analyse-data, the docroot part:
static content and libraries for the webview

%prep
%setup

%install
rm -rf  %{buildroot}
mkdir -p %{buildroot}
cp -av src/* %{buildroot}/
find %{buildroot} -type f -printf "/%%P\n" >files.lst
mkdir -p %{buildroot}/var/www/bigpicture

%files -f files.lst
%defattr(-,root,root,0755)
%attr(0775,root,admins) /var/www/bigpicture/data

%pre

%clean
%{__rm} -rf %{buildroot}

