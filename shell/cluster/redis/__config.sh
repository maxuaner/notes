#! /bin/bash
path=$(cd `dirname $0`;cd ../; pwd)
localIp=$1
cd $path
cp -R bin/. ./6379
cp -R bin/. ./6380
echo "redis复制6379和6380成功">>$path/deploy.log
cp -fp "${path}/template/redis.conf" ${path}/6379/redis.conf
echo "复制redis.conf模板到6379">>$path/deploy.log
sed -i -e  "s/#port/6379/g" -e "s/#localIp/$localIp/g"   ${path}/6379/redis.conf
echo "替换6379的redis.conf文件的变量">>$path/deploy.log
rm -f ${path}/6379/__config.sh
echo "删除6379的__config.sh">>$path/deploy.log
cp -fp "${path}/template/redis.conf" ${path}/6380/redis.conf
echo "复制redis.conf模板到6380">>$path/deploy.log
sed -i -e  "s/#port/6380/g" -e "s/#localIp/$localIp/g"   ${path}/6380/redis.conf
echo "替换6380的redis.conf文件的变量">>$path/deploy.log 
rm -f ${path}/6380/__config.sh
echo "删除6380的__config.sh">>$path/deploy.log
