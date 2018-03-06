#!/bin/bash

function install(){
    exist=$(rpm -qa | grep openresty)
    #if [ "$exist"x != ""x ];then
    #    echo openresty 已经存在被安装，退出本次安装过程
    #    #return
    #fi
    if [ -d /usr/local/openresty ];then
        echo '/usr/local/openresty文件夹已经存在,请先删除'
        return
    fi

    if [ -f /usr/local/openresty ];then
        echo '/usr/local下不允许存在名称为openresty的文件,请先删除'
        return
    fi
    basepath=$(cd `dirname $0`; cd ..; pwd)
    echo $basepath    

    rpm -i $basepath/rpm/openresty-zlib-1.2.11-2.el7.centos.x86_64.rpm
    rpm -i $basepath/rpm/openresty-openssl-1.0.2k-1.el7.centos.x86_64.rpm
    rpm -i $basepath/rpm/openresty-pcre-8.40-1.el7.centos.x86_64.rpm
    rpm -i $basepath/rpm/openresty-1.11.2.4-1.el7.centos.x86_64.rpm

    OPENRESTY='/usr/local/openresty'
    NGINX="$OPENRESTY"/nginx
    CA="$NGINX"/CA
    HTML="$NGINX"/html
    CONF="$NGINX"/conf  
    
    cp -a $basepath/nginx_config/html/*    $HTML
    cp -r $basepath/nginx_config/CA 	     $NGINX
    cp -f $basepath/nginx_config/conf/*    $CONF
    cp -f $basepath/bin/*.sh        $OPENRESTY       
    
    # 设置nginx日志循环覆盖
    cp -f $basepath/logrotate/nginx /etc/logrotate.d/nginx
	# 设置selinux，配置安全上下文
	#chcon -Rv --type=var_log_t /usr/local/openresty/nginx/logs/
    
}

install
echo "success"



