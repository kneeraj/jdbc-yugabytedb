#!/bin/bash

echoSleep() {
  echo "$1"
  SLEEP 2
}

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

  exit 1
}

pauseScript() {
    #just creating the file if it doesn't exsits
    touch .notify_shell_script

    # echo "script is paused"
    while [[ $(cat $1) != $2 ]]
    do
      dummy=1
    done
    # echo "script is continued"
  }

debugg() {
  read -p "write something and press enter to continue the script:" name
  echo "script continued"
  SLEEP 2
}
usage() { echo "Usage: $0 [-v] [-i] -D <yugabytedb-install-dir>" 1>&2; exit 1; }

VERBOSE=0
INTERACTIVE=0
INSTALL_DIR=""

while getopts ":viD:" o; do
  case "$o" in
    v)
      VERBOSE=1
      ;;
    i)
      INTERACTIVE=1
      ;;
    D)
      INSTALL_DIR=${OPTARG}
      ;;
    *)
      usage
      ;;
  esac
done

if [ -z $INSTALL_DIR ]
then
  usage
fi

check_dir=$INSTALL_DIR/bin/yb-ctl
if [ ! -f "$check_dir" ]
then
  echo "ERROR: incorrect yugabytedb directory path: $INSTALL_DIR"
  exit 1
fi

if [ $VERBOSE -eq 1 ]
then
  echoSleep "Destroying any existing cluster if present..."
fi
$INSTALL_DIR/bin/yb-ctl destroy  > yb-ctl.log 2>&1

echoSleep "Creating a 3-node, RF-3 cluster with each having some placement information"
$INSTALL_DIR/bin/yb-ctl --rf 3 create --placement_info "cloud1.region1.zone1,cloud2.region2.zone2,cloud1.region1.zone1" > yb-ctl.log 2>&1

echoSleep "Two nodes have same placement than the third one, so we will be using those two for load balancing..."

#deleting the checker file if exists
rm -rf .jdbc_example_app_checker
rm -rf .jdbc_example_app_checker2
rm -rf .jdbc_example_app_checker3 #to keep the java app running until killed

classpath=target/jdbc-yugabytedb-example-0.0.1-SNAPSHOT.jar
#Starting the Topology Aware Load Balance app
# java -cp $classpath com.yugabyte.examples.TopologyAwareLoadBalance $VERBOSE $INTERACTIVE > jdbc-yugabytedb-example.log 2>&1  &
java -cp $classpath com.yugabyte.examples.TopologyAwareLoadBalance $VERBOSE $INTERACTIVE  2>&1  &

jdbc_example_app_pid=$!

if [ $VERBOSE -eq 1 ]
then
  echoSleep "Java Example App has started running in background...."
fi

pauseScript ".notify_shell_script" "flag1"

echoSleep "Adding a node to  cluster with same placement as other two nodes...."
$INSTALL_DIR/bin/yb-ctl add_node --placement_info "cloud1.region1.zone1" >> yb-ctl.log 2>&1

touch .jdbc_example_app_checker #resuming the java app

pauseScript ".notify_shell_script" "flag2"

echoSleep "Removing a node from the cluster...."
$INSTALL_DIR/bin/yb-ctl stop_node 3 >> yb-ctl.log 2>&1

touch .jdbc_example_app_checker2 #resuming the java app

pauseScript ".notify_shell_script" "flag3"
SLEEP 2

finish $INSTALL_DIR $jdbc_example_app_pid
