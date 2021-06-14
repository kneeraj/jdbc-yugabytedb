#!/bin/bash

#file: .jdbc_example_app_checker are just used for signaling the java app whether to continue or pause at this point of time
#file: .notify_shell_script is used to signal shell script from the java whether to continue or pause at this point of time

echoSleep() {
  echo "$1"
  SLEEP 1
}

#this function will check and print the verbose statement if required
verbosePrint() {
  if [ $1 -eq 1 ]
  then
    echo "VERBOSE: $2"
  fi
}

#this function will be called at the end of the script for cleaning
finish() {
  echoSleep "End of example, destroying the created cluster...."
  $1/bin/yb-ctl destroy  >> yb-ctl.log 2>&1

  # running the remaining java app
  touch .jdbc_example_app_checker3

  ##kill the java app if exists
  kill -9 $2 >> yb-ctl.log 2>&1

  #deleting the temporary files
  rm -rf .jdbc_example_app_checker
  rm -rf .jdbc_example_app_checker2
  rm -rf .jdbc_example_app_checker3
  rm -rf .notify_shell_script
}

#this function basically checks the $file content and keep it paused until required content is present
pauseScript() {
  #just creating the file if it doesn't exsits
  file=.notify_shell_script
  touch $file

  # echo "script paused"
  while [[ $(cat $file) != $1 ]]
  do
    dummy_var=1
  done
  # echo "script continued"
}

#this function pause the script for input from user, so that user can easily view the previous commands output
interact() {
  if [ $1 -eq 1 ]
  then
    read -p "Press ENTER to continue" dummy
    SLEEP 0.2
  fi
}

VERBOSE=$1
INTERACTIVE=$2
INSTALL_DIR=$3

if [ $VERBOSE -eq 1 ]
then
  echoSleep "Destroying any existing cluster if present..."
fi
$INSTALL_DIR/bin/yb-ctl destroy  > yb-ctl.log 2>&1

echoSleep "Creating a 3-node, RF-3 cluster with each having some placement information"
$INSTALL_DIR/bin/yb-ctl --rf 3 create --placement_info "cloud1.region1.zone1,cloud1.region1.zone1,cloud2.region2.zone2" > yb-ctl.log 2>&1

echoSleep "Two nodes have same placement than the third node, so we will use the two nodes with same placement here..."

#deleting the checker files if exists
rm -rf .jdbc_example_app_checker
rm -rf .jdbc_example_app_checker2
rm -rf .jdbc_example_app_checker3 #to keep the java app running until killed
rm -rf .notify_shell_script

classpath=target/jdbc-yugabytedb-example-0.0.1-SNAPSHOT.jar
#Starting the Topology Aware Load Balance Example app
java -cp $classpath com.yugabyte.examples.TopologyAwareLoadBalanceExample $VERBOSE $INTERACTIVE  2>&1  &
# java -cp $classpath com.yugabyte.examples.TopologyAwareLoadBalance $VERBOSE $INTERACTIVE > jdbc-yugabytedb-example.log 2>&1  &

#storing the pid of the java app
jdbc_example_app_pid=$!

echoSleep "Java Example App has started running in background...."

pauseScript "flag1"

interact $INTERACTIVE

echoSleep "Adding a node to  cluster with same placement as other two nodes...."
$INSTALL_DIR/bin/yb-ctl add_node --placement_info "cloud1.region1.zone1" >> yb-ctl.log 2>&1

touch .jdbc_example_app_checker #resuming the java app

pauseScript "flag2"

interact $INTERACTIVE

echoSleep "Stopping one node in the cluster...."
$INSTALL_DIR/bin/yb-ctl stop_node 2 >> yb-ctl.log 2>&1

touch .jdbc_example_app_checker2 #resuming the java app

pauseScript "flag3"
SLEEP 2

interact $INTERACTIVE

finish $INSTALL_DIR $jdbc_example_app_pid