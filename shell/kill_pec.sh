#!/bin/bash
while true 
do
for i in `ls log/worker_job*|grep -v "worker_job1_pec2\."|grep -v "worker_job1_pec1\."|grep -v "worker_job1_pec3\."`; do
	pos=`echo "$i" | awk -F "." '{printf "%d", length($0)-length($NF)}'`
	len=$[${#i} - $pos]
	kill ${i:0-$len:$len}
	rm "$i"
	echo "kill $i"
done
sleep $[$RANDOM % 15 + 5]
done
