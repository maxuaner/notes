#! /bin/bash
basepath=$(cd `dirname $0`;cd ../; pwd)
##################安装缺少的依赖包##############
echo "正在安装缺少的依赖包"
#rpm -Uvh $basepath/rpm/*.rpm
rpm -ivh --force $basepath/rpm/*.rpm
dir="/usr/local"
cd $dir
mkdir -p keepalived
cd ${basepath}
#配置到/usr/local/keepalived
echo "正在配置configure"
sh ./configure  --prefix=/usr/local/keepalived
#编译安装
echo "正在编译安装make&makeinstall"
make && make install
#复制服务启动脚本，以便可以通过service控制keepalived服务
echo "正在复制服务启动脚本到/etc/init.d/keepalived"
cp ${dir}/keepalived/etc/rc.d/init.d/keepalived  /etc/init.d/
#复制keepalived服务脚本到默认的地址，也通过修改init.d/keepalived文件中的相应配置实现
echo "正在复制keepalived服务脚本到默认的地址/etc/sysconfig/keepalived"
cp /usr/local/keepalived/etc/sysconfig/keepalived /etc/sysconfig/
#复制默认配置文件到默认路径，其实也可以在/etc/init.d/keepalived中设置路径
echo "正在创建/etc/keepalived/"
mkdir /etc/keepalived/
echo "正在复制keepalived.conf到/etc/keepalived/"
cp /usr/local/keepalived/etc/keepalived/keepalived.conf /etc/keepalived/
chkconfig --add keepalived
#开机启动服务
chkconfig keepalived on
#-f，如果目标文件存在，就主动将目标文件直接移除后再建立
echo "正在建立链接"
ln -sf /usr/local/keepalived/sbin/keepalived /usr/sbin/
