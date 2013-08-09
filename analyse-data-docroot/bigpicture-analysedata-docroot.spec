Summary: bigpicture analysedata docroot
Name: bigpicture-analysedata-docroot
Version: 1
Release: 1
License: GPL
Vendor: Immobilien Scout GmbH
Packager: arne.hilmann@gmail.com
Group: is24
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
mkdir -p %{buildroot}/var/www

%files -f files.lst
%defattr(-,root,root,0755)

%pre

%clean
%{__rm} -rf %{buildroot}

