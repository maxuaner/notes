#! /bin/bash
basepath=$(cd `dirname $0`;cd ../; pwd)
ips=$1;
localIp=$2;
OLD_IFS="$IFS" 
IFS="," 
arr=($ips) 
IFS="$OLD_IFS"
networkCard=$3
vip=$4
date=$(date "+%Y-%m-%d %H:%M:%S")
echo "=====================脚本开始执行：${date}=====================" >>${basepath}/deploy.log
echo "虚拟ip：${vip}" >>${basepath}/deploy.log
if test ${localIp} = ${arr[0]}
then
#复制模板文件
echo "复制模板文件template.conf 到 keepalived.conf" >>${basepath}/deploy.log
cp -fp "${basepath}/template/template.conf" ${basepath}/template/keepalived.conf
echo "${localIp}是master" >>$path/deploy.log
sed -i -e "s/#nodeSign/nginx_master/g" -e "s/#number/1/g" -e "s/#isMaster/MASTER/g" -e "s/#networkCard/${networkCard}/g" -e "s/#priority/200/g" -e "s/#vip/${vip}/g" ${basepath}/template/keepalived.conf
elif test ${localIp} = ${arr[1]}
then
echo "复制模板文件template.conf 到 keepalived.conf" >>${basepath}/deploy.log
cp -fp "${basepath}/template/template.conf" ${basepath}/template/keepalived.conf
echo "${localIp}是backup" >>$path/deploy.log
sed -i -e "s/#nodeSign/nginx_backup/g" -e "s/#number/2/g"  -e "s/#isMaster/BACKUP/g" -e "s/#networkCard/${networkCard}/g" -e "s/#priority/100/g" -e "s/#vip/${vip}/g" ${basepath}/template/keepalived.conf
else
echo "unknown ${localIp} is master or backup as ${ips}" >>${basepath}/deploy.log
fi
cp -fp "${basepath}/template/keepalived.conf" /etc/keepalived/keepalived.conf
echo "替换掉/etc/keepalived/keepalived.conf的配置文件" >>${basepath}/deploy.log
cp -fp "${basepath}/template/chk_nginx.sh" /usr/local/keepalived/chk_nginx.sh
echo "复制ck_nginx.sh到/usr/local/keepalived/" >>${basepath}/deploy.log
echo "=====================脚本执行结束：${date}=====================" >>${basepath}/deploy.log
