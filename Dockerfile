FROM ubuntu:18.04

ARG PROJECT_NAME="ebiznes-serverside"
ARG GIT_REPO_URL="https://github.com/Oleksandr98/$PROJECT_NAME.git"
ARG USERNAME="oleksandr"
ARG HOME="/home/$USERNAME"
ARG PROJECT_DIR="$HOME/$PROJECT_NAME"

RUN apt update && apt install -y openjdk-8-jdk curl git
RUN curl -L -o scala-2.12.13.deb www.scala-lang.org/files/archive/scala-2.12.8.deb && dpkg -i scala-2.12.13.deb && rm scala-2.12.13.deb
RUN curl -L -o sbt-1.4.8.deb http://dl.bintray.com/sbt/debian/sbt-1.4.9.deb && dpkg -i sbt-1.4.8.deb && rm sbt-1.4.8.deb

RUN useradd -ms /bin/bash $USERNAME
RUN cd $HOME && git clone $GIT_REPO_URL && chown -R $USERNAME $PROJECT_DIR
WORKDIR $PROJECT_DIR
USER $USERNAME
RUN sbt stage

EXPOSE 8081

VOLUME ["$PROJECT_DIR"]