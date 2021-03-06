/**
 * Copyright (C) 2011-2012 Turn, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.turn.ttorrent.common.protocol;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.text.ParseException;
import java.util.BitSet;

import com.turn.ttorrent.client.SharedTorrent;
//import bencode.BDict;
//import bencode.BDecoder;
import com.turn.ttorrent.client.peer.SharingPeer;

/**
 * BitTorrent peer protocol messages representations.
 *
 * <p>
 * This class and its <em>*Messages</em> subclasses provide POJO
 * representations of the peer protocol messages, along with easy parsing from
 * an input ByteBuffer to quickly get a usable representation of an incoming
 * message.
 * </p>
 *
 * @author mpetazzoni
 * @see <a href="http://wiki.theory.org/BitTorrentSpecification#Peer_wire_protocol_.28TCP.29">BitTorrent peer wire protocol</a>
 */
public abstract class PeerMessage {

	/** The size, in bytes, of the length field in a message (one 32-bit
	 * integer). */
	public static final int MESSAGE_LENGTH_FIELD_SIZE = 4;

	/**
	 * Message type.
	 *
	 * <p>
	 * Note that the keep-alive messages don't actually have an type ID defined
	 * in the protocol as they are of length 0.
	 * </p>
	 */
	public enum Type {
		KEEP_ALIVE(-1),
		CHOKE(0),
		UNCHOKE(1),
		INTERESTED(2),
		NOT_INTERESTED(3),
		HAVE(4),
		BITFIELD(5),
		REQUEST(6),
		PIECE(7),
		CANCEL(8),
		PORT(9),
		EXTENSION(20);

		private byte id;
		Type(int id) {
			this.id = (byte)id;
		}

		public boolean equals(byte c) {
			return this.id == c;
		}

		public byte getTypeByte() {
			return this.id;
		}

		public static Type get(byte c) {
			for (Type t : Type.values()) {
				if (t.equals(c)) {
					return t;
				}
			}
			return null;
		}
	};

	private final Type type;
	private final ByteBuffer data;

	private PeerMessage(Type type, ByteBuffer data) {
		this.type = type;
		this.data = data;
		this.data.rewind();
	}

	public Type getType() {
		return this.type;
	}

	/**
	 * Returns a {@link ByteBuffer} backed by the same data as this message.
	 *
	 * <p>
	 * This method returns a duplicate of the buffer stored in this {@link
	 * PeerMessage} object to allow for multiple consumers to read from the
	 * same message without conflicting access to the buffer's position, mark
	 * and limit.
	 * </p>
	 */
	public ByteBuffer getData() {
		return this.data.duplicate();
	}

	/**
	 * Validate that this message makes sense for the torrent it's related to.
	 *
	 * <p>
	 * This method is meant to be overloaded by distinct message types, where
	 * it makes sense. Otherwise, it defaults to true.
	 * </p>
	 *
	 * @param torrent The torrent this message is about.
	 */
	public PeerMessage validate(SharedTorrent torrent)
		throws MessageValidationException {
		return this;
	}

	public String toString() {
		return this.getType().name();
	}

	/**
	 * Parse the given buffer into a peer protocol message.
	 *
	 * <p>
	 * Parses the provided byte array and builds the corresponding PeerMessage
	 * subclass object.
	 * </p>
	 *
	 * @param buffer The byte buffer containing the message data.
	 * @param torrent The torrent this message is about.
	 * @param peer The peer associated with the message
	 * @return A PeerMessage subclass instance.
	 * @throws ParseException When the message is invalid, can't be parsed or
	 * does not match the protocol requirements.
	 */
	public static PeerMessage parse(ByteBuffer buffer, SharedTorrent torrent, SharingPeer peer)
		throws ParseException {
		int length = buffer.getInt();
		if (length == 0) {
			return KeepAliveMessage.parse(buffer, torrent, peer);
		} else if (length != buffer.remaining()) {
			throw new ParseException("Message size did not match announced " +
					"size!", 0);
		}

		Byte typeByte = buffer.get();
		Type type = Type.get(typeByte);
		if (type == null) {
			throw new ParseException("Unknown message ID! ID byte='"+typeByte+"'",
					buffer.position()-1);
		}

		switch (type) {
			case CHOKE:
				return ChokeMessage.parse(buffer.slice(), torrent, peer);
			case UNCHOKE:
				return UnchokeMessage.parse(buffer.slice(), torrent, peer);
			case INTERESTED:
				return InterestedMessage.parse(buffer.slice(), torrent, peer);
			case NOT_INTERESTED:
				return NotInterestedMessage.parse(buffer.slice(), torrent, peer);
			case HAVE:
				return HaveMessage.parse(buffer.slice(), torrent, peer);
			case BITFIELD:
				return BitfieldMessage.parse(buffer.slice(), torrent, peer);
			case REQUEST:
				return RequestMessage.parse(buffer.slice(), torrent, peer);
			case PIECE:
				return PieceMessage.parse(buffer.slice(), torrent, peer);
			case CANCEL:
				return CancelMessage.parse(buffer.slice(), torrent, peer);
			case PORT:
				return PortMessage.parse(buffer.slice(), torrent, peer);
			case EXTENSION:
				return ExtensionMessage.parse(buffer.slice(), torrent, peer);
			default:
				throw new IllegalStateException("Message type should have " +
						"been properly defined by now.");
		}
	}

	public static class MessageValidationException extends ParseException {

		static final long serialVersionUID = -1;

		public MessageValidationException(PeerMessage m) {
			super("Message " + m + " is not valid!", 0);
		}

	}


	/**
	 * Keep alive message.
	 *
	 * <len=0000>
	 */
	public static class KeepAliveMessage extends PeerMessage {

		private static final int BASE_SIZE = 0;

		private KeepAliveMessage(ByteBuffer buffer) {
			super(Type.KEEP_ALIVE, buffer);
		}

		public static KeepAliveMessage parse(ByteBuffer buffer,
				SharedTorrent torrent, SharingPeer peer) throws MessageValidationException {
			return (KeepAliveMessage)new KeepAliveMessage(buffer)
				.validate(torrent);
		}

		public static KeepAliveMessage craft() {
			ByteBuffer buffer = ByteBuffer.allocateDirect(
				MESSAGE_LENGTH_FIELD_SIZE + KeepAliveMessage.BASE_SIZE);
			buffer.putInt(KeepAliveMessage.BASE_SIZE);
			return new KeepAliveMessage(buffer);
		}
	}

	/**
	 * Choke message.
	 *
	 * <len=0001><id=0>
	 */
	public static class ChokeMessage extends PeerMessage {

		private static final int BASE_SIZE = 1;

		private ChokeMessage(ByteBuffer buffer) {
			super(Type.CHOKE, buffer);
		}

		public static ChokeMessage parse(ByteBuffer buffer,
				SharedTorrent torrent, SharingPeer peer) throws MessageValidationException {
			return (ChokeMessage)new ChokeMessage(buffer)
				.validate(torrent);
		}

		public static ChokeMessage craft() {
			ByteBuffer buffer = ByteBuffer.allocateDirect(
				MESSAGE_LENGTH_FIELD_SIZE + ChokeMessage.BASE_SIZE);
			buffer.putInt(ChokeMessage.BASE_SIZE);
			buffer.put(PeerMessage.Type.CHOKE.getTypeByte());
			return new ChokeMessage(buffer);
		}
	}

	/**
	 * Unchoke message.
	 *
	 * <len=0001><id=1>
	 */
	public static class UnchokeMessage extends PeerMessage {

		private static final int BASE_SIZE = 1;

		private UnchokeMessage(ByteBuffer buffer) {
			super(Type.UNCHOKE, buffer);
		}

		public static UnchokeMessage parse(ByteBuffer buffer,
				SharedTorrent torrent, SharingPeer peer) throws MessageValidationException {
			return (UnchokeMessage)new UnchokeMessage(buffer)
				.validate(torrent);
		}

		public static UnchokeMessage craft() {
			ByteBuffer buffer = ByteBuffer.allocateDirect(
				MESSAGE_LENGTH_FIELD_SIZE + UnchokeMessage.BASE_SIZE);
			buffer.putInt(UnchokeMessage.BASE_SIZE);
			buffer.put(PeerMessage.Type.UNCHOKE.getTypeByte());
			return new UnchokeMessage(buffer);
		}
	}

	/**
	 * Interested message.
	 *
	 * <len=0001><id=2>
	 */
	public static class InterestedMessage extends PeerMessage {

		private static final int BASE_SIZE = 1;

		private InterestedMessage(ByteBuffer buffer) {
			super(Type.INTERESTED, buffer);
		}

		public static InterestedMessage parse(ByteBuffer buffer,
				SharedTorrent torrent, SharingPeer peer) throws MessageValidationException {
			return (InterestedMessage)new InterestedMessage(buffer)
				.validate(torrent);
		}

		public static InterestedMessage craft() {
			ByteBuffer buffer = ByteBuffer.allocateDirect(
				MESSAGE_LENGTH_FIELD_SIZE + InterestedMessage.BASE_SIZE);
			buffer.putInt(InterestedMessage.BASE_SIZE);
			buffer.put(PeerMessage.Type.INTERESTED.getTypeByte());
			return new InterestedMessage(buffer);
		}
	}

	/**
	 * Not interested message.
	 *
	 * <len=0001><id=3>
	 */
	public static class NotInterestedMessage extends PeerMessage {

		private static final int BASE_SIZE = 1;

		private NotInterestedMessage(ByteBuffer buffer) {
			super(Type.NOT_INTERESTED, buffer);
		}

		public static NotInterestedMessage parse(ByteBuffer buffer,
				SharedTorrent torrent, SharingPeer peer) throws MessageValidationException {
			return (NotInterestedMessage)new NotInterestedMessage(buffer)
				.validate(torrent);
		}

		public static NotInterestedMessage craft() {
			ByteBuffer buffer = ByteBuffer.allocateDirect(
				MESSAGE_LENGTH_FIELD_SIZE + NotInterestedMessage.BASE_SIZE);
			buffer.putInt(NotInterestedMessage.BASE_SIZE);
			buffer.put(PeerMessage.Type.NOT_INTERESTED.getTypeByte());
			return new NotInterestedMessage(buffer);
		}
	}

	/**
	 * Have message.
	 *
	 * <len=0005><id=4><piece index=xxxx>
	 */
	public static class HaveMessage extends PeerMessage {

		private static final int BASE_SIZE = 5;

		private int piece;

		private HaveMessage(ByteBuffer buffer, int piece) {
			super(Type.HAVE, buffer);
			this.piece = piece;
		}

		public int getPieceIndex() {
			return this.piece;
		}

		@Override
		public HaveMessage validate(SharedTorrent torrent)
			throws MessageValidationException {
			if (this.piece >= 0 && this.piece < torrent.getPieceCount()) {
				return this;
			}

			throw new MessageValidationException(this);
		}

		public static HaveMessage parse(ByteBuffer buffer,
				SharedTorrent torrent, SharingPeer peer) throws MessageValidationException {
			return new HaveMessage(buffer, buffer.getInt())
				.validate(torrent);
		}

		public static HaveMessage craft(int piece) {
			ByteBuffer buffer = ByteBuffer.allocateDirect(
				MESSAGE_LENGTH_FIELD_SIZE + HaveMessage.BASE_SIZE);
			buffer.putInt(HaveMessage.BASE_SIZE);
			buffer.put(PeerMessage.Type.HAVE.getTypeByte());
			buffer.putInt(piece);
			return new HaveMessage(buffer, piece);
		}

		public String toString() {
			return super.toString() + " #" + this.getPieceIndex();
		}
	}

	/**
	 * Bitfield message.
	 *
	 * <len=0001+X><id=5><bitfield>
	 */
	public static class BitfieldMessage extends PeerMessage {

		private static final int BASE_SIZE = 1;

		private BitSet bitfield;

		private BitfieldMessage(ByteBuffer buffer, BitSet bitfield) {
			super(Type.BITFIELD, buffer);
			this.bitfield = bitfield;
		}

		public BitSet getBitfield() {
			return this.bitfield;
		}

		@Override
		public BitfieldMessage validate(SharedTorrent torrent)
			throws MessageValidationException {
			if (this.bitfield.length() <= torrent.getPieceCount()) {
				return this;
			}

			throw new MessageValidationException(this);
		}

		public static BitfieldMessage parse(ByteBuffer buffer,
				SharedTorrent torrent, SharingPeer peer) throws MessageValidationException {
			BitSet bitfield = new BitSet(buffer.remaining()*8);
			for (int i=0; i < buffer.remaining()*8; i++) {
				if ((buffer.get(i/8) & (1 << (7 -(i % 8)))) > 0) {
					bitfield.set(i);
				}
			}

			return new BitfieldMessage(buffer, bitfield)
				.validate(torrent);
		}

		public static BitfieldMessage craft(BitSet availablePieces) {
			byte[] bitfield = new byte[
				(int) Math.ceil((double)availablePieces.length()/8)];
			for (int i=availablePieces.nextSetBit(0); i >= 0;
					i=availablePieces.nextSetBit(i+1)) {
				bitfield[i/8] |= 1 << (7 -(i % 8));
			}

			ByteBuffer buffer = ByteBuffer.allocateDirect(
				MESSAGE_LENGTH_FIELD_SIZE + BitfieldMessage.BASE_SIZE + bitfield.length);
			buffer.putInt(BitfieldMessage.BASE_SIZE + bitfield.length);
			buffer.put(PeerMessage.Type.BITFIELD.getTypeByte());
			buffer.put(ByteBuffer.wrap(bitfield));
			return new BitfieldMessage(buffer, availablePieces);
		}

		public String toString() {
			return super.toString() + " " + this.getBitfield().cardinality();
		}
	}

	/**
	 * Request message.
	 *
	 * <len=00013><id=6><piece index><block offset><block length>
	 */
	public static class RequestMessage extends PeerMessage {

		private static final int BASE_SIZE = 13;

		/** Default block size is 2^14 bytes, or 16kB. */
		public static final int DEFAULT_REQUEST_SIZE = 16384;

		/** Max block request size is 2^17 bytes, or 131kB. */
		public static final int MAX_REQUEST_SIZE = 131072;

		private int piece;
		private int offset;
		private int length;

		private RequestMessage(ByteBuffer buffer, int piece,
				int offset, int length) {
			super(Type.REQUEST, buffer);
			this.piece = piece;
			this.offset = offset;
			this.length = length;
		}

		public int getPiece() {
			return this.piece;
		}

		public int getOffset() {
			return this.offset;
		}

		public int getLength() {
			return this.length;
		}

		@Override
		public RequestMessage validate(SharedTorrent torrent)
			throws MessageValidationException {
			if (this.piece >= 0 && this.piece < torrent.getPieceCount() &&
				this.offset + this.length <=
					torrent.getPiece(this.piece).size()) {
				return this;
			}

			throw new MessageValidationException(this);
		}

		public static RequestMessage parse(ByteBuffer buffer,
				SharedTorrent torrent, SharingPeer peer) throws MessageValidationException {
			int piece = buffer.getInt();
			int offset = buffer.getInt();
			int length = buffer.getInt();
			return new RequestMessage(buffer, piece,
					offset, length).validate(torrent);
		}

		public static RequestMessage craft(int piece, int offset, int length) {
			ByteBuffer buffer = ByteBuffer.allocateDirect(
				MESSAGE_LENGTH_FIELD_SIZE + RequestMessage.BASE_SIZE);
			buffer.putInt(RequestMessage.BASE_SIZE);
			buffer.put(PeerMessage.Type.REQUEST.getTypeByte());
			buffer.putInt(piece);
			buffer.putInt(offset);
			buffer.putInt(length);
			return new RequestMessage(buffer, piece, offset, length);
		}

		public String toString() {
			return super.toString() + " #" + this.getPiece() +
				" (" + this.getLength() + "@" + this.getOffset() + ")";
		}
	}

	/**
	 * Piece message.
	 *
	 * <len=0009+X><id=7><piece index><block offset><block data>
	 */
	public static class PieceMessage extends PeerMessage {

		private static final int BASE_SIZE = 9;

		private int piece;
		private int offset;
		private ByteBuffer block;

		private PieceMessage(ByteBuffer buffer, int piece,
				int offset, ByteBuffer block) {
			super(Type.PIECE, buffer);
			this.piece = piece;
			this.offset = offset;
			this.block = block;
		}

		public int getPiece() {
			return this.piece;
		}

		public int getOffset() {
			return this.offset;
		}

		public ByteBuffer getBlock() {
			return this.block;
		}

		@Override
		public PieceMessage validate(SharedTorrent torrent)
			throws MessageValidationException {
			if (this.piece >= 0 && this.piece < torrent.getPieceCount() &&
				this.offset + this.block.limit() <=
				torrent.getPiece(this.piece).size()) {
				return this;
			}

			throw new MessageValidationException(this);
		}

		public static PieceMessage parse(ByteBuffer buffer,
				SharedTorrent torrent, SharingPeer peer) throws MessageValidationException {
			int piece = buffer.getInt();
			int offset = buffer.getInt();
			ByteBuffer block = buffer.slice();
			return new PieceMessage(buffer, piece, offset, block)
				.validate(torrent);
		}

		public static PieceMessage craft(int piece, int offset,
				ByteBuffer block) {
			ByteBuffer buffer = ByteBuffer.allocateDirect(
				MESSAGE_LENGTH_FIELD_SIZE + PieceMessage.BASE_SIZE + block.capacity());
			buffer.putInt(PieceMessage.BASE_SIZE + block.capacity());
			buffer.put(PeerMessage.Type.PIECE.getTypeByte());
			buffer.putInt(piece);
			buffer.putInt(offset);
			buffer.put(block);
			return new PieceMessage(buffer, piece, offset, block);
		}

		public String toString() {
			return super.toString() + " #" + this.getPiece() +
				" (" + this.getBlock().capacity() + "@" + this.getOffset() + ")";
		}
	}

	/**
	 * Cancel message.
	 *
	 * <len=00013><id=8><piece index><block offset><block length>
	 */
	public static class CancelMessage extends PeerMessage {

		private static final int BASE_SIZE = 13;

		private int piece;
		private int offset;
		private int length;

		private CancelMessage(ByteBuffer buffer, int piece,
				int offset, int length) {
			super(Type.CANCEL, buffer);
			this.piece = piece;
			this.offset = offset;
			this.length = length;
		}

		public int getPiece() {
			return this.piece;
		}

		public int getOffset() {
			return this.offset;
		}

		public int getLength() {
			return this.length;
		}

		@Override
		public CancelMessage validate(SharedTorrent torrent)
			throws MessageValidationException {
			if (this.piece >= 0 && this.piece < torrent.getPieceCount() &&
				this.offset + this.length <=
					torrent.getPiece(this.piece).size()) {
				return this;
			}

			throw new MessageValidationException(this);
		}

		public static CancelMessage parse(ByteBuffer buffer,
				SharedTorrent torrent, SharingPeer peer) throws MessageValidationException {
			int piece = buffer.getInt();
			int offset = buffer.getInt();
			int length = buffer.getInt();
			return new CancelMessage(buffer, piece,
					offset, length).validate(torrent);
		}

		public static CancelMessage craft(int piece, int offset, int length) {
			ByteBuffer buffer = ByteBuffer.allocateDirect(
				MESSAGE_LENGTH_FIELD_SIZE + CancelMessage.BASE_SIZE);
			buffer.putInt(CancelMessage.BASE_SIZE);
			buffer.put(PeerMessage.Type.CANCEL.getTypeByte());
			buffer.putInt(piece);
			buffer.putInt(offset);
			buffer.putInt(length);
			return new CancelMessage(buffer, piece, offset, length);
		}

		public String toString() {
			return super.toString() + " #" + this.getPiece() +
				" (" + this.getLength() + "@" + this.getOffset() + ")";
		}
	}

	/**
	 * Port message.
	 *
	 * <len=0003><id=9><listen-port>
	 */
	public static class PortMessage extends PeerMessage {

		private static final int BASE_SIZE = 3;

		private Integer dhtPort;
		private SharingPeer peer;

		private PortMessage(ByteBuffer buffer, Integer dhtPort, SharingPeer peer) {
			super(Type.PORT, buffer);
			this.dhtPort = dhtPort;
		}

		public int getPort() {
			return this.dhtPort;
		}

		@Override
		public PortMessage validate(SharedTorrent torrent)
			throws MessageValidationException {
			if (this.dhtPort == null) {
				throw new MessageValidationException(this);
			}
			return this;
		}
		
		public static PortMessage parse(ByteBuffer buffer,
				SharedTorrent torrent, SharingPeer peer) throws MessageValidationException {
			Integer dhtPort = null;
			if (buffer.remaining() >= 2) {
				byte b1 = buffer.get();
				byte b2 = buffer.get();
				
				dhtPort = (b2 & 0xFF) << 8 | (b1 & 0xFF);
				/*
				dhtPort = buffer.getShort();
				if (!ByteOrder.nativeOrder().equals(ByteOrder.BIG_ENDIAN)) {
					ByteBuffer b = ByteBuffer.allocate(2);
					b.putShort(dhtPort);
					b.flip();
					dhtPort = b.getShort();
				}
				*/
			}
			System.err.println("HOST="+peer.getIp()+", DHTPORT="+dhtPort);
			return new PortMessage(buffer, dhtPort, peer).validate(torrent);
		}

		public String toString() {
			return super.toString() + "H=" + this.peer.getIp() + ", P=" + this.dhtPort;
		}
	}

	
	/**
	 * Extension message.
	 * <len=0000><id=20><ext_id=uint8_t>
	 *
	 */
	public static class ExtensionMessage extends PeerMessage {
		
		private static final int BASE_ID = 1;

		private ExtensionMessage(ByteBuffer buffer) {
			super(Type.EXTENSION, buffer);
		}
		
		public static ExtensionMessage parse(ByteBuffer buffer,
				SharedTorrent torrent, SharingPeer peer) throws MessageValidationException {
			System.err.println("EXTENSION");
			int msgId = buffer.get();
			System.err.println("MSG ID="+msgId);
			
			try {
				Charset charset = Charset.forName("ISO-8859-1");
				
				CharBuffer cbuf;
				CharsetDecoder decoder = charset.newDecoder();

				ByteBuffer x = buffer.slice();
				byte[] y = new byte[x.limit()];
				x.get(y);
				ByteBuffer z = ByteBuffer.wrap(y);
				
				cbuf = decoder.decode(z);

//				CharBuffer cb = CharBuffer.wrap(x.asCharBuffer().array());
				if ( cbuf.toString().indexOf("added") > 0)
					System.err.println(cbuf.toString());
			} catch (CharacterCodingException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

			
			
/*
			if (msgId == 0) {
				try {
					
//		            BDict bdict = (BDict)(new BDecoder(cbuf.toString())).parse();
//		            System.err.println(bdict.prettyPrint());
		            
					BEValue decoded = BDecoder.bdecode(z);
					Map<String,BEValue> dat = decoded.getMap();
					for (String k : dat.keySet()) {
						System.err.println("k="+k+" v="+dat.get(k));
					}
					if (dat.containsKey("m")) {
						Map<String,BEValue> mdat = dat.get("m").getMap();
						for (String j : mdat.keySet()) {
							System.err.println("  k="+j+" v="+mdat.get(j));
						}
						if (mdat.containsKey("ut_pex")) {
							System.err.println("    k="+mdat.get("ut_pex"));
//							Map<String,BEValue> pdat = mdat.get("ut_pex").getMap();
//							for (String i : pdat.keySet()) {
//								System.err.println("    k="+i+" v="+pdat.get(i));
//							}
							
						}
					}
			
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
*/
			ByteBuffer msg = buffer.slice();
//			System.err.println("MSG="+msg.asCharBuffer());
			return (ExtensionMessage)new ExtensionMessage(msg)
				.validate(torrent);
		}

		public static ExtensionMessage craft() {
			ByteBuffer buffer = ByteBuffer.allocateDirect(
				MESSAGE_LENGTH_FIELD_SIZE + ExtensionMessage.BASE_ID);
			buffer.putInt(ExtensionMessage.BASE_ID);
			buffer.put(PeerMessage.Type.EXTENSION.getTypeByte());
			return new ExtensionMessage(buffer);
		}
	}
}
