#! /bin/sh
path=$(cd `dirname $0`;cd ../; pwd)
ips=$1;
localIp=$2;
OLD_IFS="$IFS" 
IFS="," 
arr=($ips) 
IFS="$OLD_IFS"
i=0
$server
for((i=1;i<=${#arr[@]};i++))
do
	server=${server}"server.${i}=${arr[i-1]}:28888:3888\n"
	if test ${localIp} = ${arr[i-1]}
	then
		echo "${i}">${path}/tmp/zookeeper/myid	
		echo "${path}/tmp/zookeeper/myid写入${i}成功">>${path}/deploy.log	
	fi
done
sed -i "s/serverList/$server/g" ${path}/template/zoo.cfg
echo "${path}/template/zoo.cfg中serverList替换${server}成功">>${path}/deploy.log
if test ! -d "$path/bak"
then
	mkdir "${path}/bak"
	echo "创建bak目录用于备份">>${path}/deploy.log
fi
cp -fP "${path}/conf/zoo.cfg"  ${path}/bak/zoo.cfg
echo "复制${path}/conf/zoo.cfg到${path}/bak/zoo.cfg">>${path}/deploy.log
cp -fp "${path}/template/zoo.cfg" "${path}/conf/zoo.cfg"
echo "复制${path}/template/zoo.cfg到${path}/conf/zoo.cfg">>${path}/deploy.log
