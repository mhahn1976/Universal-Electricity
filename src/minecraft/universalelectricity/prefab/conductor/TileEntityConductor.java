package universalelectricity.prefab.conductor;

import java.util.EnumSet;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.INetworkManager;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.Packet250CustomPayload;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.ForgeDirection;

import org.bouncycastle.util.Arrays;

import universalelectricity.core.electricity.Electricity;
import universalelectricity.core.electricity.ElectricityConnections;
import universalelectricity.core.electricity.ElectricityNetwork;
import universalelectricity.core.implement.IConductor;
import universalelectricity.core.vector.Vector3;
import universalelectricity.prefab.microblock.TileEntityMicroblock;
import universalelectricity.prefab.network.IPacketReceiver;
import universalelectricity.prefab.network.PacketManager;

import com.google.common.io.ByteArrayDataInput;

/**
 * This tile entity pre-fabricated for all conductors.
 * 
 * @author Calclavia
 * 
 */
public abstract class TileEntityConductor extends TileEntityMicroblock implements IConductor, IPacketReceiver
{
	private ElectricityNetwork network;

	/**
	 * Used client side to render.
	 */
	public boolean[] visuallyConnected = { false, false, false, false, false, false };

	/**
	 * Stores information on the blocks that this conductor is connected to.
	 */
	public TileEntity[] connectedBlocks = { null, null, null, null, null, null };

	protected String channel = "";

	public TileEntityConductor()
	{
		ElectricityConnections.registerConnector(this, EnumSet.range(ForgeDirection.DOWN, ForgeDirection.EAST));
		this.reset();
	}

	@Override
	public ElectricityNetwork getNetwork()
	{
		return this.network;
	}

	@Override
	public void setNetwork(ElectricityNetwork network)
	{
		this.network = network;
	}

	@Override
	public TileEntity[] getConnectedBlocks()
	{
		return this.connectedBlocks;
	}

	@Override
	public void updateConnection(TileEntity tileEntity, ForgeDirection side)
	{
		if (!this.worldObj.isRemote)
		{
			if (tileEntity != null)
			{
				if (ElectricityConnections.canConnect(tileEntity, side.getOpposite()))
				{
					this.connectedBlocks[side.ordinal()] = tileEntity;
					this.visuallyConnected[side.ordinal()] = true;

					if (tileEntity.getClass() == this.getClass())
					{
						Electricity.instance.mergeConnection(this.getNetwork(), ((IConductor) tileEntity).getNetwork());
					}

					return;
				}
			}

			if (this.connectedBlocks[side.ordinal()] != null)
			{
				if (this.connectedBlocks[side.ordinal()] instanceof IConductor)
				{
					Electricity.instance.splitConnection(this, (IConductor) this.getConnectedBlocks()[side.ordinal()]);
				}

				this.getNetwork().stopProducing(this.connectedBlocks[side.ordinal()]);
				this.getNetwork().stopRequesting(this.connectedBlocks[side.ordinal()]);
			}

			this.connectedBlocks[side.ordinal()] = null;
			this.visuallyConnected[side.ordinal()] = false;
		}
	}

	@Override
	public void updateConnectionWithoutSplit(TileEntity tileEntity, ForgeDirection side)
	{
		if (!this.worldObj.isRemote)
		{
			if (tileEntity != null)
			{
				if (ElectricityConnections.canConnect(tileEntity, side.getOpposite()))
				{
					this.connectedBlocks[side.ordinal()] = tileEntity;
					this.visuallyConnected[side.ordinal()] = true;

					if (tileEntity.getClass() == this.getClass())
					{
						Electricity.instance.mergeConnection(this.getNetwork(), ((IConductor) tileEntity).getNetwork());
					}

					return;
				}
			}

			this.connectedBlocks[side.ordinal()] = null;
			this.visuallyConnected[side.ordinal()] = false;
		}
	}

	@Override
	public void handlePacketData(INetworkManager network, int type, Packet250CustomPayload packet, EntityPlayer player, ByteArrayDataInput dataStream)
	{
		if (this.worldObj.isRemote)
		{
			this.visuallyConnected[0] = dataStream.readBoolean();
			this.visuallyConnected[1] = dataStream.readBoolean();
			this.visuallyConnected[2] = dataStream.readBoolean();
			this.visuallyConnected[3] = dataStream.readBoolean();
			this.visuallyConnected[4] = dataStream.readBoolean();
			this.visuallyConnected[5] = dataStream.readBoolean();
		}
	}

	@Override
	public void initiate()
	{
		//TODO: REMOVE THIS
		this.isOccupied[0] = true;
		this.refreshConnectedBlocks();
	}

	@Override
	public void updateEntity()
	{
		super.updateEntity();

		if (this.ticks % 300 == 0)
		{
			this.refreshConnectedBlocks();
		}

		//TODO: REMOVE THIS
		for (int i = 0; i < 6; i++)
		{
			if (this.isOccupied[i])
			{
				Vector3 position = new Vector3(this);
				position.add(0.5);
				position.modifyPositionFromSide(ForgeDirection.getOrientation(i), 0.3);
				this.worldObj.spawnParticle("smoke", position.x, position.y, position.z, 0, 0, 0);
			}
		}
	}

	@Override
	public void reset()
	{
		this.network = null;

		if (Electricity.instance != null)
		{
			Electricity.instance.registerConductor(this);
		}
	}

	@Override
	public void refreshConnectedBlocks()
	{
		if (this.worldObj != null)
		{
			if (!this.worldObj.isRemote)
			{
				boolean[] previousConnections = this.visuallyConnected.clone();

				for (int side = 0; side < 6; side++)
				{
					if (this.isOccupied[side])
					{
						for (byte i = 0; i < 6; i++)
						{
							Vector3 position = new Vector3(this);
							ForgeDirection direction = ForgeDirection.getOrientation(i);
							this.updateConnection(Vector3.getConnectorFromSide(this.worldObj, new Vector3(this), direction), direction);

							//position.modifyPositionFromSide(direction);
							//this.updateConnection(Vector3.getConnectorFromSide(this.worldObj, position, direction), direction);

						}
					}
				}

				/**
				 * Only send packet updates if visuallyConnected changed.
				 */
				if (!Arrays.areEqual(previousConnections, this.visuallyConnected))
				{
					this.worldObj.markBlockForUpdate(this.xCoord, this.yCoord, this.zCoord);
				}
			}

		}
	}

	@Override
	public Packet getDescriptionPacket()
	{
		return PacketManager.getPacket(this.channel, this, this.visuallyConnected[0], this.visuallyConnected[1], this.visuallyConnected[2], this.visuallyConnected[3], this.visuallyConnected[4], this.visuallyConnected[5]);
	}
}