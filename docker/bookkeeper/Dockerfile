#
#  Copyright (c) 2017 Dell Inc., or its subsidiaries.
#

FROM java:8
MAINTAINER Arvind Kandhare [arvind.kandhare@emc.com]

RUN apt-get update \
&&  apt-get install -y wget unzip \
&&  mkdir -p /opt \
&&  cd /opt

RUN wget http://www.apache.org/dist/bookkeeper/bookkeeper-4.4.0/bookkeeper-server-4.4.0-bin.tar.gz \
&& tar xvzf  bookkeeper-server-4.4.0-bin.tar.gz \
&& mkdir -p /opt/bk_all \
&& mv bookkeeper-server-4.4.0/ /opt/bk_all/

RUN wget http://www.apache.org/dist/zookeeper/zookeeper-3.5.1-alpha/zookeeper-3.5.1-alpha.tar.gz \
&& tar xvzf  zookeeper-3.5.1-alpha.tar.gz \
&& mkdir -p /opt/zk \
&& mv zookeeper-3.5.1-alpha/ /opt/zk/

ENV bookiePort 3181

EXPOSE $bookiePort

# all execute-able bin, download from apache BK and ZK
WORKDIR /opt/bk_all

# After container up, Following instructions to build bookie cluster
COPY entrypoint.sh /opt/bk_all/entrypoint.sh
ENTRYPOINT /opt/bk_all/entrypoint.sh
