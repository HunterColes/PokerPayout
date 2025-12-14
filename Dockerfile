# F-Droid compatible build environment
FROM ubuntu:22.04

# Prevent interactive prompts
ENV DEBIAN_FRONTEND=noninteractive

# Install Java 17 and basic tools
RUN apt-get update && \
    apt-get install -y openjdk-17-jdk-headless git dos2unix && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

# Set Java 17 as default
RUN update-java-alternatives -a

# Set environment variables
ENV JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
ENV ANDROID_HOME=/opt/android-sdk
ENV PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/build-tools/34.0.0

# Install Android SDK
RUN mkdir -p $ANDROID_HOME/cmdline-tools && \
    cd $ANDROID_HOME/cmdline-tools && \
    apt-get update && apt-get install -y wget unzip && \
    wget -q https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip && \
    unzip -q commandlinetools-linux-11076708_latest.zip && \
    rm commandlinetools-linux-11076708_latest.zip && \
    mv cmdline-tools latest && \
    apt-get clean && rm -rf /var/lib/apt/lists/*

# Accept licenses and install SDK components
RUN yes | sdkmanager --licenses || true
RUN sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0"

# Working directory
WORKDIR /workspace

# Keep container running
CMD ["/bin/bash"]
