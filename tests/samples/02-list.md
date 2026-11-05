● 高德地图_点亮城市
am start -d 'amapuri://ajx?path=path://amap_bundle_mine/src/pages/FootprintPage.page.js&data={"from":"scan","cardName":"city"}'

● 获取当前无障碍服务
v='secure enabled_accessibility_services'
echo "settings put $v $(settings get $v)"
