# Dali Clock for Android, Copyright (c) 2015 by Robin Müller-Cajar.

TITLE		= Dali Clock
TARGET		= DaliClock
TARGET2		= daliclock
VENDOR		= Jamie Zawinski (Robin Müller-Cajar)
VERSION		= $(shell sed -n \
			's/^.*\([0-9][0-9]*\.[0-9][0-9]*\).*$$/\1/p' \
			< ../version.h)
VERSION_NUM	=  $(shell sed -n \
			's/^.*\([0-9][0-9]*\)\.\([0-9][0-9]*\).*$$/\1\2/p' \
			< ../version.h)
TOOLS_VERSION	= $(shell sed -n \
			's/^.*buildToolsVersion."\([^"]*\)".*$$/\1/p' \
			< $(TARGET)/app/build.gradle)
ID		= org.jwz.$(TARGET2)
APK		= $(TARGET2)_$(VERSION).apk
CERT		= my-keystore.keystore

CC		= gcc
CFLAGS		= -g -Wall -Wstrict-prototypes -Wnested-externs
LDFLAGS		= 
DEFINES		= -DBUILTIN_FONTS
INCLUDES	= -I. -I../font -I../OSX
OBJS		= buildnumbers.o

FONTDIR		= $(TARGET)/app/src/main/res/raw
NUMBERS		= $(FONTDIR)/font0.json \
		  $(FONTDIR)/font1.json \
		  $(FONTDIR)/font2.json \
		  $(FONTDIR)/font3.json \
		  $(FONTDIR)/font4.json \
		  $(FONTDIR)/font5.json \
		  $(FONTDIR)/font6.json \
		  $(FONTDIR)/font7.json \

default: apk

clean:
	rm -f *.o buildnumbers

gradle_clean:
	cd $(TARGET); ./gradlew clean

distclean:: clean

distclean:: gradle_clean

distclean::
	rm -f *.apk

distclean::
	find . -name '.gitignore' -exec rm '{}' \;

distclean::
	rm -rf $(TARGET)/build
	rm -f $(TARGET)/local.properties
	rm -f $(TARGET)/.idea/workspace.xml
	rm -rf $(TARGET)/.idea/libraries
	rm -rf $(TARGET)/.gradle
	rm -f $(TARGET)/.DS_Store
	rm -f $(TARGET)/app/manifest-merger-release-report.txt
	rm -rf $(TARGET)/app/libs
	rm -rf sign-*


distdepend:: update_gradle_version update_gradle_app_id

echo_tarfiles:
	@echo `find . \
	  \( \( -name '*~*' -o -name '*.o' \
		-o -name buildnumbers -o -name '*.apk' \
		-o -name '*report.txt' \
		-o -name 'local.properties' \
		-o -name 'workspace.xml' \
		-o -name '.gradle' \
		-o -name 'libraries' \
		-o -name '.DS_Store' \
		-o -name 'build' \
	      \) \
	     -prune \) \
	  -o -type f -print \
	| sed 's@^\./@@' \
	| sort`


buildnumbers.o:
	$(CC) -c $(INCLUDES) $(DEFINES) $(CFLAGS) ../font/buildnumbers.c

buildnumbers: buildnumbers.o
	$(CC) $(LDFLAGS) -o $@ $(OBJS)

$(NUMBERS): buildnumbers

$(FONTDIR)/font0.json:
	./buildnumbers 0 > $@
$(FONTDIR)/font1.json:
	./buildnumbers 1 > $@
$(FONTDIR)/font2.json:
	./buildnumbers 2 > $@
$(FONTDIR)/font3.json:
	./buildnumbers 3 > $@
$(FONTDIR)/font4.json:
	./buildnumbers 4 > $@
$(FONTDIR)/font5.json:
	./buildnumbers 5 > $@
$(FONTDIR)/font6.json:
	./buildnumbers 6 > $@
$(FONTDIR)/font7.json:
	./buildnumbers 7 > $@

all:: $(NUMBERS)


# Create my-keystore.keystore with:
#   keytool -genkey -v -keystore my-keystore.keystore -alias daliclock \
#           -keyalg RSA -keysize 2048 -validity 10000
# Keep that file secret!  It is your private key.
# You can of course use any existing keystore if you already have one.

sign: get_zipalign
	@\
  if [ ! -f "$(CERT)" ]; then							\
    /bin/echo "$(CERT) does not exist! Generate one. See Makefile." ;		\
    exit 1 ;									\
  fi ;										\
  /bin/echo -n "Signing  \"$(APK)\"... " ;					\
  STAGE=sign-$$$$ ;							 	\
  rm -rf $$STAGE ;								\
  mkdir $$STAGE ;								\
  cd $$STAGE ;									\
  mv ../$(TARGET)/app/build/outputs/apk/app-release-unsigned.apk ./$(APK);	\
  jarsigner -verbose -sigalg SHA1withRSA -digestalg SHA1 -keystore ../$(CERT)	\
    $(APK) $(TARGET2);								\
  if [ ! -f "$(ZIPALIGN)" ]; then						\
    /bin/echo "Cannot find zipalign in build-tools:"; 				\
    /bin/echo "$(ZIPALIGN)"; 							\
    /bin/echo "As ANDROID_HOME is valid, please check you have got the build-tools defined in"; \
    /bin/echo "$(TARGET)/app/build.gradle";					\
    /bin/echo "Or make build-tools part of your PATH variable";			\
    /bin/echo "";								\
    /bin/echo "The created apk will still work, but it might use more memory than neccessary!"; \
    mv $(APK) ../$(APK);							\
  else										\
    $(ZIPALIGN) -v 4 $(APK) $(APK).aligned; 					\
    mv $(APK).aligned ../$(APK);						\
  fi ;										\
  cd ..;								      	\
  rm -rf $$STAGE

get_zipalign:
  ifndef ZIPALIGN
    ZIPALIGN = $(shell which zipalign 2>&- )
  endif
  ifeq ($(strip $(ZIPALIGN)),)
    ZIPALIGN = $(ANDROID_HOME)/build-tools/$(TOOLS_VERSION)/zipalign
  endif
  

apk: apk_raw sign

apk_raw: $(NUMBERS) ../version.h update_gradle_version update_gradle_app_id make_gradle_executable check_android_home
	cd $(TARGET); ./gradlew assembleRelease

check_android_home:
  ifndef ANDROID_HOME
    ANDROID_HOME    := $(shell sed -n \
			's/^.*sdk\.dir.\(.*\)[ ]*$$/\1\//p' \
			< $(TARGET)/local.properties)
  endif
  ifeq ($(strip $(ANDROID_HOME)),)
    $(error Cannot find the Android SDK - Please set the ANDROID_HOME variable to your android-sdk directory) 
  endif

make_gradle_executable:
	chmod +x $(TARGET)/gradlew

update_gradle_app_id:
	@S="$(TARGET)/app/build.gradle" ;					\
  /bin/echo -n "Updating application id in $$S to $(ID)... " ;			\
  T=/tmp/xs.$$$$ ;								\
  sed -e "s/^\(.*applicationId.*\"\).*\(\".*\)$$/\1$(ID)\2/"	      		\
     < $$S > $$T ;								\
  if cmp -s $$S $$T ; then							\
    echo "unchanged." ;								\
  else										\
    cat $$T > $$S ;								\
    echo "done." ;								\
  fi ;										\
  rm $$T

update_gradle_version:
	@S="$(TARGET)/app/build.gradle" ;					\
  DATE=`date +%d-%h-%Y` ;							\
  /bin/echo -n "Updating version in $$S to $$VERSION/$$DATE... " ;		\
  T=/tmp/xs.$$$$ ;								\
  sed -e "s/^\(.*versionCode .*\)[0-9][0-9]*\(.*\)$$/\1$$VERSION_NUM\2/"	\
      -e "s/^\(.*versionName.*\"\).*\(\".*\)$$/\1$$VERSION\/$$DATE\2/"		\
     < $$S > $$T ;								\
  if cmp -s $$S $$T ; then							\
    echo "unchanged." ;								\
  else										\
    cat $$T > $$S ;								\
    echo "done." ;								\
  fi ;										\
  rm $$T


