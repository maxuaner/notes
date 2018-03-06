#!/bin/bash

function print_help() {
    cat <<EOF
    
Usage: 
[ options ]
    --http-port=xx			nginx监听的http端口
    --https-port=xx			nginx监听的https端口
    --CA				生成证书
    --init-referers=xx,xx		初始化防盗链,填所有服务器IP
    --add-referers=xx,xx		增加防盗链
    -h, --help                          显示此帮助信息并退出

EOF
    exit 0
}

HTTP_PORT=''
HTTPS_PORT=''
VALID_REFERERS=''
VALID_REFERERS_TYPE=''
virtualIp=''
basepath=$(cd `dirname $0`;cd ../; pwd)
date=$(date "+%Y-%m-%d %H:%M:%S")
pwd=/usr/local/openresty
CA_DIR="$pwd"/nginx/CA/
CONF_DIR="$pwd"/nginx/conf/
OPENSSL_DIR="$pwd"/openssl/bin/
OPENSSL_LIB="$pwd"/openssl/lib/
ZLIB_LIB="$pwd"/zlib/lib
function parse_args(){
	echo "=====================脚本开始执行：${date}=====================" >>${basepath}/deploy.log
    if [[ $# -lt 1 ]];then
        echo missing parameter >>${basepath}/deploy.log
        print_help
        exit 1
    fi

    for arg in $@
    do
        case $arg in
        *http-port=*)
          echo 正在解析http端口 >>${basepath}/deploy.log
          HTTP_PORT=$(echo $arg | sed 's/--http-port=//')
          config_http_port
        ;;
        *https-port=*)
          echo 正在解析https端口 >>${basepath}/deploy.log
          HTTPS_PORT=$(echo $arg |  sed 's/--https-port=//')
        config_https_port
        ;;
        *init-referers=*)
          echo 正在解析防盗链IP >>${basepath}/deploy.log
          VALID_REFERERS=$(echo $arg | sed "s/--init-referers=//" | sed "s/,/ /g")
          config_valid_referers
        ;; 
        *add-referers=*)
          echo 正在解析要新增的防盗链IP >>${basepath}/deploy.log
          VALID_REFERERS=$(echo $arg | sed "s/--add-referers=//" | sed "s/,/ /g")
          VALID_REFERERS_TYPE='add'
          config_valid_referers
        ;;
		*vip=*)
		echo  正在解析虚拟ip >>${basepath}/deploy.log
		vip=$(echo $arg | sed "s/--vip=//" | sed "s/,/ /g")
		virtualIp="$vip"
		config_vip
		;;
		*kmsip=*)
		echo  正在解析文件服务器ip >>${basepath}/deploy.log
		kmsip=$(echo $arg | sed "s/--kmsip=//" | sed "s/,/ /g")
		config_kmsip
		;;
		*impip=*)
		echo 正在解析集群集成平台服务列表，已在蜂鸟配置。。>>${basepath}/deploy.log
		# impip_serverList=$(echo $arg | sed "s/--impip=//" | sed "s/,/ /g")
		# config_impip
		;;
        *--CA)
          echo 配置服务端证书 >>${basepath}/deploy.log
          config_CA
        ;;
        -h|--help)
          print_help
        ;;
        *)
          echo invalid paramter $arg
        ;;
        esac
    done
    echo "=====================脚本执行结束：${date}=====================" >>${basepath}/deploy.log
}

function config_http_port(){
    echo 开始配置http端口$HTTP_PORT >>${basepath}/deploy.log
    if [ -e "$CONF_DIR"http_params.conf ];then
        cp -f "$CONF_DIR"/http_params.conf "$CONF_DIR"/http_params.conf.bk
    else   
        echo http_params配置文件丢失 >>${basepath}/deploy.log
        return
    fi  
       
    sed -i -r "s/listen ([0-9]*);/listen $HTTP_PORT;/g" "$CONF_DIR"/http_params.conf

}


function config_https_port(){
    echo 开始配置https端口$HTTPS_PORT >>${basepath}/deploy.log

    if [ -e "$CONF_DIR"https_params.conf ];then
        cp -f "$CONF_DIR"/https_params.conf "$CONF_DIR"/https_params.conf.bk
    else
        echo https_params配置文件丢失 >>${basepath}/deploy.log
        return
    fi
    
    sed -i -r "s/listen [0-9]* ssl/listen $HTTPS_PORT ssl/g" "$CONF_DIR"/https_params.conf 
    sed -i -r "s/set.*HTTPS_PORT [0-9]*;/set \$HTTPS_PORT $HTTPS_PORT;/g" "$CONF_DIR"/http_params.conf
}

function config_valid_referers()
{
    echo 开始配置防盗链地址$VALID_REFERERS >>${basepath}/deploy.log
    echo "$CONF_DIR"https_params >>${basepath}/deploy.log
    if [ -e "$CONF_DIR"https_params.conf ];then
        cp -f $CONF_DIR/https_params.conf $CONF_DIR/https_params.conf.bk
    else
        echo https_params配置文件丢失 >>${basepath}/deploy.log
        return
    fi

    echo REFERERS $VALID_REFERERS_TYPE >>${basepath}/deploy.log
    if [ "$VALID_REFERERS_TYPE"x == ""x ];then
        echo 初始化防盗链列表 >>${basepath}/deploy.log
        sed -i -r "s/valid_referers none blocked.*;/valid_referers none blocked $VALID_REFERERS;/g" "$CONF_DIR"/https_params.conf
    else
        echo 追加防盗链列表 >>${basepath}/deploy.log
        sed -i -r "s/valid_referers none blocked /valid_referers none blocked $VALID_REFERERS /g" "$CONF_DIR"/https_params.conf
    fi


}

function config_vip(){
	echo 开始配置虚拟ip$vip >>${basepath}/deploy.log
	echo "$CONF_DIR"nginx.conf >>${basepath}/deploy.log
	if [ -e "$CONF_DIR"nginx.conf ];then
		cp -f $CONF_DIR/nginx.conf $CONF_DIR/nginx.conf.bk
		sed -i -r "s/server_name ([0-9]*\.[0-9]*\.[0-9]*\.[0-9]*);/server_name $vip;/g" "$CONF_DIR"/nginx.conf
		sed -i -r "s/server_name ([0-9]*\.[0-9]*\.[0-9]*\.[0-9]*);/server_name $vip;/g" "$CONF_DIR"/https_params.conf
		sed -i -r "s/server_name ([0-9]*\.[0-9]*\.[0-9]*\.[0-9]*);/server_name $vip;/g" "$CONF_DIR"/http_params.conf
	else
        echo nginx.conf配置文件丢失 >>${basepath}/deploy.log
        return
    fi
}

function config_kmsip(){
	echo 开始配置文件服务器$kmsip >>${basepath}/deploy.log
	echo "$CONF_DIR"nginx.conf >>${basepath}/deploy.log
	if [ -e "$CONF_DIR"nginx.conf ];then
		sed -i -r "s/([0-9]*\.[0-9]*\.[0-9]*\.[0-9]*)\:8080\/kms/$kmsip\:8080\/kms/g" "$CONF_DIR"/https_params.conf
	else
        echo nginx.conf配置文件丢失 >>${basepath}/deploy.log
        return
    fi
}

function config_impip()
{
	echo 开始配置集成平台服务地址$impip_serverList >>${basepath}/deploy.log
	echo "$CONF_DIR"nginx.conf >>${basepath}/deploy.log
	if [ -e "$CONF_DIR"nginx.conf ];then
		OLD_IFS="$IFS" 
		IFS="," 
		arr=($impip_serverList) 
		IFS="$OLD_IFS"
		for((i=0;i<${#arr[@]};i++))
		do
			apolloStreamParams=${apolloStreamParams}"server  ${arr[i]}:1288;\n"
			apolloHttpParams=${apolloHttpParams}"server ${arr[i]}:8087;\n"
			casHttpParams=${casHttpParams}"server ${arr[i]}:8082;\n"
		done				
		sed -i -r "s/server ([0-9]*.[0-9]*.[0-9]*.[0-9]*:1288);/$apolloStreamParams;/g" "$CONF_DIR"/nginx.conf
		sed -i -r "s/server ([0-9]*.[0-9]*.[0-9]*.[0-9]*:8087);/$apolloHttpParams;/g" "$CONF_DIR"/nginx.conf
		sed -i -r "s/server ([0-9]*.[0-9]*.[0-9]*.[0-9]*:8082);/$casHttpParams;/g" "$CONF_DIR"/nginx.conf
    else
        echo nginx.conf配置文件丢失 >>${basepath}/deploy.log
        return
    fi
}
function config_CA()
{
    echo 开始生成服务器证书 >>${basepath}/deploy.log
    #export LD_LIBRARY_PATH="$OPENSSL_LIB":"$ZLIB_LIB":$LD_LIBRARY_PATH
    #echo export | grep LD_LIBRARY_PATH
    #cp -f ./openssl/openssl.cnf ./
    #if [ ! -e "$CA_DIR"server.key ];then
        echo 开始生成server.key >>${basepath}/deploy.log
        "$OPENSSL_DIR"/openssl genrsa -out "$CA_DIR"/server.key 2048      
        if [ ! -e "$CA_DIR"server.key ];then
            echo 生成server.key失败 >>${basepath}/deploy.log
            return
        fi
    #fi

    #if [ ! -e "$CA_DIR"server.csr ];then
        echo 生成server.csr >>${basepath}/deploy.log
        "$OPENSSL_DIR"/openssl req -new -key "$CA_DIR"/server.key -subj "/C=CN/ST=ZheJiang/L=HangZhou/O=Hikvision/OU=GA/CN=ga.hikvision.com" -out "$CA_DIR"/server.csr
    #fi

    #if [ ! -e "$CA_DIR"san.cnf ];then
        cp -f "$CA_DIR"san.cnf.sample "$CA_DIR"san.cnf
    #fi

    local_ip=$(ip addr | grep 'inet [0-9]*.[0-9]*.[0-9]*.[0-9]*/[0-9]*' | awk '{print $2}' | awk -F'/' '{print $1}')
	flag=false
    i=1
    for ip in $local_ip
    do
        if [[ "$ip" == "127.0.0.1" ]];then
            continue
        fi
		if [[ "$ip" == "${virtualIp}" ]];then
           flag=true
        fi
        exist=$(cat "$CA_DIR"/san.cnf | grep $ip)
        if [ -z "$exist" ];then
            echo -e "IP.$i=$ip" >> "$CA_DIR"/san.cnf
            echo -e "DNS.$i=$ip">> "$CA_DIR"/san.cnf
        fi
        ((i++))
    done
	#集群需要添加虚拟ip到证书
	if test "${flag}" != true;then
       echo -e "IP.$i=${virtualIp}" >> "$CA_DIR"/san.cnf
       echo -e "DNS.$i=${virtualIp}">> "$CA_DIR"/san.cnf
    fi
    #使用根证书签发服务端证书，证书默认有效期1825天（5年）
    "$OPENSSL_DIR"openssl x509 -req -in "$CA_DIR"server.csr -CA "$CA_DIR"root.crt -CAkey "$CA_DIR"root.key -CAcreateserial -out "$CA_DIR"server.crt -days 1825 -extensions SAN -extfile "$CA_DIR"san.cnf

   
    echo "$OPENSSL_DIR"/openssl x509 -noout -text -in "$CA_DIR"/server.crt  
    #crt_diff=$(diff <(tail -n 2 "$CA_DIR"root.crt) <(tail -n 2 "$CA_DIR"server.crt))
    #if [ "$crt_diff"x != ""x ];then
        echo 将根证书追加到服务端证书后
        cat  "$CA_DIR"/root.crt >> "$CA_DIR"/server.crt
    #fi
}
    

parse_args $*
