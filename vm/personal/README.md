== Overview as a VM ==

We run Overview on Netty. This is meant to be a low-traffic server, so we do
not add any extra layers such as nginx. Java is enough.

We create this directory structure on the VM:

* `/opt/overview/lib`: A bunch of jars
* `/opt/overview/run`: Runtime files (PIDs, etc)
* `/opt/overview/log`: Logfiles
* `/opt/overview/script/start-worker`: Starts the worker
* `/opt/overview/script/start-server`: Starts the server
* `/opt/overview/application.conf`: configuration file; disables external services such as email and Google Analytics
* `/etc/init.d/overview-worker`: Starts/stops the worker process
* `/etc/init.d/overview-server`: Starts/stops the server process

== How to build the VM ==

Run `make`. This will download the necessary files and run the install.

== How to use it ==

1. Download VirtualBox
2. Download the provided "./ova" file
3. "Import appliance" in VirtualBox, and import the ovf file
4. Start the machine
5. Browse to http://localhost:6837

== What's on the VM ==

When the VM starts, VirtualBox opens two ports on the host:

* `localhost:6837` ("OVER" on a keypad): the Overview server.
* `localhost:6836`: SSH backdoor, username `overview`, password `overview`

These ports are only open to localhost. Other computers on the host (user's)
machine won't be able to connect to them.

== How this directory works ==

We do this:

1. Install Ubuntu on the VM.
2. Copy `dist.zip`, which is produced by running `play dist`, to
   `/home/overview/` on the VM.
3. Copy `vm-files.tar.gz`, created from this subdirectory, to
   `/home/overview/` on the VM.
4. Copy `vm-setup.sh` to `/home/overview/` on the VM.
5. Execute `/home/overview/vm-setup.sh` on the VM, as root.
6. Leave the VM running, so you can test it.