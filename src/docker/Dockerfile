FROM tomcat:8.5-jre17

RUN apt-get update && apt-get install -y unzip curl && rm -rf /var/lib/apt/lists/*

# add TEAM engine webapp
ADD maven/dependency/teamengine-web-*.war /root/
RUN cd /root/ && unzip -q teamengine-web-*.war -d /usr/local/tomcat/webapps/teamengine
RUN sed -i 's| aliases="[^"]*"||' /usr/local/tomcat/webapps/teamengine/META-INF/context.xml \
 && sed -i '/<WatchedResource>/i\
  <Resources className="org.apache.catalina.webresources.StandardRoot">\
    <PostResources className="org.apache.catalina.webresources.DirResourceSet" webAppMount="/docs" base="/root/te_base/resources/docs" internalPath="/" />\
    <PostResources className="org.apache.catalina.webresources.DirResourceSet" webAppMount="/about" base="/root/te_base/scripts" internalPath="/" />\
    <PostResources className="org.apache.catalina.webresources.DirResourceSet" webAppMount="/reports" base="/root/te_base/users" internalPath="/" />\
    <PostResources className="org.apache.catalina.webresources.DirResourceSet" webAppMount="/site" base="/root/te_base/resources/site" internalPath="/" />\
  </Resources>' /usr/local/tomcat/webapps/teamengine/META-INF/context.xml
RUN sed -i '/<Loader className="org.apache.catalina.loader.VirtualWebappLoader"/,/\/>/d' \
    /usr/local/tomcat/webapps/teamengine/META-INF/context.xml
RUN find /usr/local/tomcat/webapps/teamengine -name '*.jsp' -type f \
    -exec sed -i 's/e\.printStackTrace(System\.out)/e.printStackTrace()/g' {} +
RUN find /usr/local/tomcat/webapps/teamengine -name '*.jsp' -type f \
    -exec sed -i 's/System\.getProperty("file\.separator")/java.io.File.separator/g' {} +

# add common libs
ADD maven/dependency/teamengine-web-*-common-libs.zip /root/
RUN cd /root/ && unzip -q teamengine-web-*-common-libs.zip -d /usr/local/tomcat/lib
RUN cd /usr/local/tomcat/lib \
 && curl -fsSL "https://repo.maven.apache.org/maven2/javax/xml/bind/jaxb-api/2.3.1/jaxb-api-2.3.1.jar" -o jaxb-api-2.3.1.jar \
 && curl -fsSL "https://repo.maven.apache.org/maven2/com/sun/xml/bind/jaxb-core/2.3.0.1/jaxb-core-2.3.0.1.jar" -o jaxb-core-2.3.0.1.jar \
 && curl -fsSL "https://repo.maven.apache.org/maven2/com/sun/xml/bind/jaxb-impl/2.3.1/jaxb-impl-2.3.1.jar" -o jaxb-impl-2.3.1.jar \
 && curl -fsSL "https://repo.maven.apache.org/maven2/javax/activation/javax.activation-api/1.2.0/javax.activation-api-1.2.0.jar" -o javax.activation-api-1.2.0.jar

# add TEAM engine console
ADD maven/dependency/teamengine-console-*-base.zip /root/
RUN cd /root/ && unzip -q teamengine-console-*-base.zip -d /root/te_base

# set TE_BASE
ENV JAVA_OPTS="-Xms1024m -Xmx2048m -DTE_BASE=/root/te_base"

# add ETS
ADD maven/ets-swecommon30-*-ctl.zip /root/
RUN cd /root/ && unzip -q ets-swecommon30-*-ctl.zip -d /root/te_base/scripts
ADD maven/ets-swecommon30-*-deps.zip /root/
RUN cd /root/ && unzip -q -o ets-swecommon30-*-deps.zip -d /usr/local/tomcat/webapps/teamengine/WEB-INF/lib

# run tomcat
CMD ["catalina.sh", "jpda", "run"]
