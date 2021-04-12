# GoBackNT
A basic implementation of Go-Back-N protocol with a sender and a receiver

## How it works
There are two java classes GBNTSender and GBNTReceiver. The first is the sender that implement transport.Sender interface and the main functions are unreliableReceive to handle packets from the network, reliableSend and close. The second one is the receiver that implements the unrealibleReceive method to handle packets from the sender.
