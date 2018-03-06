#! /bin/bash
path=$(cd `dirname $0`;cd ../; pwd)
ips=$1
localIp=$2
OLD_IFS="$IFS" 
IFS="," 
arr=($ips) 
IFS="$OLD_IFS"
i=0
#分配槽(主节点)
if test ${#arr[@]} -ne 3
then
echo "redis集群主机不是三台，error">>$path/deploy.log
else
#分配槽(主节点)
echo "正在${arr[0]}的6379分配{0..5460}">>$path/deploy.log
"$path"/bin/redis-cli -h "${arr[0]}" -p 6379 cluster addslots {0..5460}
echo "正在${arr[1]}的6379分配{5461..10921}">>$path/deploy.log
"$path"/bin/redis-cli -h "${arr[1]}" -p 6379 cluster addslots {5461..10921}
echo "正在${arr[2]}的6379分配{10922..16383}">>$path/deploy.log
"$path"/bin/redis-cli -h "${arr[2]}" -p 6379 cluster addslots {10922..16383}
echo "分配槽结束">>$path/deploy.log
#握手
 for ((i=1;i<=${#arr[@]};i++))
   do
	if test $localIp = ${arr[i-1]}
	then
		echo "${arr[i-1]}的6380正在握手$localIp 6379">>$path/deploy.log
		"$path"/bin/redis-cli -h "${arr[i-1]}" -p 6380 cluster meet "$localIp" 6379
	else
		echo "${arr[i-1]}的6379正在握手$localIp 6379">>$path/deploy.log
		"$path"/bin/redis-cli -h "${arr[i-1]}" -p 6379 cluster meet "$localIp" 6379
		echo "${arr[i-1]}的6380正在握手$localIp 6379">>$path/deploy.log
		"$path"/bin/redis-cli -h "${arr[i-1]}" -p 6380 cluster meet "$localIp" 6379	
	fi
	masterId=$("$path"/bin/redis-cli -h "${arr[i-1]}" -p 6379 cluster nodes | grep myself | awk '{print $1}') 	
	masterIds[$i-1]=$masterId;
done
   echo "握手结束">>$path/deploy.log
#睡眠2秒，等待握手结束   
sleep 2s
#指定主从
j=0
  for ((j=1;j<=${#arr[@]};j++))
  do
    if test $j -eq ${#arr[@]}
    then
	echo "${arr[0]}的6380是${masterIds[j-1]}的从">>$path/deploy.log
	"$path"/bin/redis-cli -h "${arr[0]}" -p 6380 cluster replicate "${masterIds[j-1]}"
    else
	echo "${arr[j]}的6380是${masterIds[j-1]}的从">>$path/deploy.log
       "$path"/bin/redis-cli -h "${arr[j]}" -p 6380 cluster replicate "${masterIds[j-1]}"
    fi    
  done
  echo "指定主从结束">>$path/deploy.log
     
fi
